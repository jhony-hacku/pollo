import { membersWithRole } from './geofence.js';
import { claimOnce, sendToUsers } from './push.js';
import { isRecent } from './transactionMessage.js';

const RESTART_DELAY_MS = 30_000;
const PREVIEW_LENGTH = 200;

export const CHAT_MESSAGE = 'chat_message';

/** El aviso se envía al otro participante de la conversación. */
export function chatRecipients({ members, workerUid, senderUid }) {
  if (senderUid === workerUid) {
    return membersWithRole(members, 'OWNER');
  }

  return membersWithRole(members, 'WORKER')
    .filter((uid) => uid === workerUid);
}

/** Construye el título mostrado en la notificación. */
export function chatTitle(sender) {
  if (sender?.displayName) {
    return `Mensaje de ${sender.displayName}`;
  }

  return sender?.role === 'OWNER'
    ? 'Mensaje del propietario'
    : 'Mensaje de un trabajador';
}

/** Nombre corto utilizado por el estilo de conversación de Android. */
export function chatSenderName(sender) {
  if (sender?.displayName) {
    return sender.displayName;
  }

  return sender?.role === 'OWNER'
    ? 'Propietario'
    : 'Trabajador';
}

/**
 * Mensaje FCM de solo datos. La aplicación crea la notificación
 * y abre la conversación correspondiente cuando el usuario la toca.
 */
export function buildChatMessage({
  farmId,
  workerUid,
  title,
  text,
  senderName = '',
  photoUrl = '',
}) {
  const body = text.length > PREVIEW_LENGTH
    ? `${text.slice(0, PREVIEW_LENGTH)}…`
    : text;

  return {
    data: {
      type: CHAT_MESSAGE,
      farmId: String(farmId),
      workerUid: String(workerUid),
      title: String(title),
      body: String(body),
      senderName: String(senderName),
      photoUrl: String(photoUrl),
    },
    android: {
      priority: 'high',
      ttl: 24 * 60 * 60 * 1000,
    },
  };
}

/**
 * Escucha las conversaciones y avisa al otro participante
 * cuando llega un mensaje nuevo.
 */
export class ChatNotifier {
  constructor({
    db,
    messaging,
    config,
    log = console.log,
    now = Date.now,
  }) {
    this.db = db;
    this.messaging = messaging;
    this.config = config;
    this.log = log;
    this.now = now;
    this.stats = {
      listening: false,
      sent: 0,
      failed: 0,
    };
  }

  start() {
    this.unsubscribe = this.db
      .collectionGroup('chats')
      .onSnapshot(
        (snapshot) => {
          this.stats.listening = true;

          for (const change of snapshot.docChanges()) {
            if (change.type === 'removed') {
              continue;
            }

            this.handle(change.doc).catch((error) => {
              this.stats.failed += 1;
              this.log(
                `ERROR avisando ${change.doc.ref.path}: ${error.stack ?? error}`,
              );
            });
          }
        },
        (error) => {
          this.stats.listening = false;
          this.log(
            `ERROR del listener del chat (reintento en 30 s): ${error.message}`,
          );

          setTimeout(
            () => this.start(),
            RESTART_DELAY_MS,
          ).unref();
        },
      );
  }

  stop() {
    this.unsubscribe?.();
    this.stats.listening = false;
  }

  async handle(doc) {
    const farmId = doc.ref.parent.parent?.id;
    const {
      lastMessage,
      lastMessageAt,
      lastSenderUid,
    } = doc.data() ?? {};

    if (!farmId || !lastMessage || !lastSenderUid) {
      return;
    }

    if (
      !isRecent(
        { createdAt: lastMessageAt },
        this.now(),
        this.config.maxAgeMinutes,
      )
    ) {
      return;
    }

    const claimId =
      `chat_${farmId}_${doc.id}_${lastMessageAt}`;

    const claimed = await claimOnce(
      this.db,
      claimId,
      {
        farmId,
        workerUid: doc.id,
      },
    );

    if (!claimed) {
      return;
    }

    const membersSnapshot = await this.db
      .collection('farms')
      .doc(farmId)
      .collection('members')
      .get();

    const members = membersSnapshot.docs.map((member) => ({
      id: member.id,
      role: member.get('role'),
      isActive: member.get('isActive'),
      displayName: member.get('displayName'),
      photoUrl: member.get('photoUrl'),
    }));

    const sender = members.find(
      (member) => member.id === lastSenderUid,
    );

    const recipientUids = chatRecipients({
      members,
      workerUid: doc.id,
      senderUid: lastSenderUid,
    });

    const message = buildChatMessage({
      farmId,
      workerUid: doc.id,
      title: chatTitle(sender),
      text: lastMessage,
      senderName: chatSenderName(sender),
      photoUrl: sender?.photoUrl ?? '',
    });

    const result = await sendToUsers({
      db: this.db,
      messaging: this.messaging,
      uids: recipientUids,
      message,
      log: this.log,
    });

    this.stats.sent += result.sent;
    this.stats.failed += result.failed;

    this.log(
      `${farmId}/chats/${doc.id}: mensaje avisado a ` +
        `${result.sent}/${result.targets} dispositivos`,
    );
  }
}