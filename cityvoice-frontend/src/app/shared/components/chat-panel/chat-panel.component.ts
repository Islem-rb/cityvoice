import {
  Component, OnInit, OnDestroy,
  ViewChildren, QueryList, ElementRef, AfterViewChecked
} from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService, ChatMsg } from '../../../core/services/chat.service';
import { FriendService, FriendDto } from '../../../core/services/friend.service';
import { UserService } from '../../../core/services/user.service';
import { GroupChatService, ChatGroup, GroupMessage } from '../../../core/services/group-chat.service';
import { BadWordsService } from '../../../core/services/bad-words.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { CallService } from '../../../core/services/call.service';
import { SoundService } from '../../../core/services/sound.service';

export interface ChatContact {
  id: string;
  nom: string;
  photo?: string;
  unreadCount: number;
}

export interface OpenChat {
  contact: ChatContact;
  messages: ChatMsg[];
  input: string;
  isMinimized: boolean;
  msgsSub?: Subscription;
  lastSeenId: number;
}

export interface OpenGroupChat {
  group: ChatGroup;
  messages: GroupMessage[];
  input: string;
  isMinimized: boolean;
  msgsSub?: Subscription;
  unreadCount: number;
  showMembers?: boolean;
}

export interface ChatNotif {
  contactName: string;
  contactId: string;
  preview: string;
}

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChildren('msgList') msgLists!: QueryList<ElementRef>;

  isLoggedIn = false;
  currentUserId = '';
  currentUserName = '';
  currentUserPhoto = '';

  // Tab actif : 'contacts' | 'groups'
  activeTab: 'contacts' | 'groups' = 'contacts';

  contacts: ChatContact[]         = [];
  filteredContacts: ChatContact[] = [];
  searchQuery = '';

  openChats: OpenChat[] = [];

  // Groupes
  groups: ChatGroup[]         = [];
  filteredGroups: ChatGroup[] = [];
  groupSearchQuery = '';
  openGroupChats: OpenGroupChat[] = [];

  // Modal création groupe
  showCreateGroupModal = false;
  newGroupName = '';
  selectedFriendIds: Set<string> = new Set();
  friends: FriendDto[] = [];

  notification: ChatNotif | null = null;
  private notifTimer?: any;

  private subs: Subscription[] = [];
  private shouldScroll = false;

  // Résolution des noms des membres de groupe (userId → nom)
  memberNames = new Map<string, string>();
  memberPhotos = new Map<string, string>();

  // Message de feedback visible
  actionMsg: { text: string; isError: boolean } | null = null;
  private actionMsgTimer?: any;

  // Couleurs pour les senders dans les groupes
  private senderColors = [
    '#E85D24', '#2196F3', '#4CAF50', '#9C27B0',
    '#FF9800', '#00BCD4', '#E91E63', '#607D8B'
  ];
  private colorMap = new Map<string, string>();

  // IDs des contacts bloqués par le user courant
  blockedContactIds = new Set<string>();
  blockedContacts: FriendDto[] = [];          // liste complète avec nom/photo
  showBlockedPanel = false;                   // toggle panneau bloqués dans la sidebar

  // Appels vocaux
  incomingCall: { callerId: string; callerName: string; callerPhoto: string; channelName: string; token: string | null } | null = null;
  activeCall: { channelName: string; contactName: string; contactId: string; isMuted: boolean } | null = null;
  private readonly AGORA_APP_ID = 'bb9c42005efb477f904ad0d1ebaf04da';

  // Sonnerie appel entrant
  private ringtone: HTMLAudioElement = new Audio('ringtone.wav');


  // Ajouter membres groupe
  showAddMembersPanel = false;
  friendsNotInGroup: FriendDto[] = [];
  selectedFriendsToAdd = new Set<string>();

  constructor(
    private authService: AuthService,
    private chatService: ChatService,
    private friendService: FriendService,
    private userService: UserService,
    private groupChatService: GroupChatService,
    private badWords: BadWordsService,
    private wsService: WebSocketService,
    public callService: CallService,
    private sound: SoundService
  ) {}

  ngOnInit(): void {
    this.isLoggedIn = this.authService.isLoggedIn();
    const user = this.authService.getCurrentUser();
    if (!user?.userId) return;

    this.currentUserId = user.userId;
    this.currentUserName = user.email || '';   // valeur par défaut en attendant le chargement

    this.chatService.init(this.currentUserId);
    this.groupChatService.init(this.currentUserId);

    // Charger le nom et la photo du profil courant
    this.userService.getById(this.currentUserId).subscribe({
      next: (u) => {
        this.currentUserName  = u.nom  || user.email || '';
        this.currentUserPhoto = u.photo || '';
      },
      error: () => {}
    });

    this.loadFriendsAsContacts();
    this.loadGroups();
    this.loadBlockedContacts();

    // Écouter les appels entrants via WebSocket
    const wsSub = this.wsService.call$.subscribe((payload: any) => {
      console.log('[WS] call$ received:', payload);
      if (payload.type === 'INCOMING_CALL') {
        this.incomingCall = {
          callerId:    payload.callerId    || payload.actorId,
          callerName:  payload.callerName  || payload.actorName || 'Quelqu\'un',
          callerPhoto: payload.callerPhoto || payload.actorPhoto || '',
          channelName: payload.channelName,
          token:       payload.token       || null
        };
        this.playRingtone();
      } else if (payload.type === 'CALL_REJECTED') {
        this.stopRingtone();
        if (this.activeCall) {
          this.showActionMsg('📵 Appel refusé');
          this.endCallLocally();
        }
        if (this.incomingCall) {
          this.incomingCall = null;
          this.showActionMsg('📵 Appel annulé');
        }
      } else if (payload.type === 'CALL_ENDED') {
        this.stopRingtone();
        if (this.activeCall) {
          this.showActionMsg('📞 Appel terminé');
          this.endCallLocally();
        }
        if (this.incomingCall) {
          this.incomingCall = null;
          this.showActionMsg('📞 Appel terminé');
        }
      }
    });
    this.subs.push(wsSub);

    // Polling unread + seen toutes les 3s
    const pollSub = interval(3000).subscribe(() => {
      this.contacts.forEach(c => this.checkUnread(c));
      this.openChats.forEach(chat => this.checkSeen(chat));
    });
    this.subs.push(pollSub);

    // Rafraîchir amis + groupes toutes les 30s
    const refreshSub = interval(30000).subscribe(() => {
      this.loadFriendsAsContacts();
      this.loadGroups();
    });
    this.subs.push(refreshSub);
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.openChats.forEach(c => c.msgsSub?.unsubscribe());
    this.openGroupChats.forEach(c => c.msgsSub?.unsubscribe());
    if (this.notifTimer) clearTimeout(this.notifTimer);
    if (this.actionMsgTimer) clearTimeout(this.actionMsgTimer);
    this.stopRingtone();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollAllToBottom();
      this.shouldScroll = false;
    }
  }

  // ===== TABS =====
  setTab(tab: 'contacts' | 'groups'): void {
    this.activeTab = tab;
  }

  // ===== CHARGER LES AMIS =====
  loadFriendsAsContacts(): void {
    this.friendService.getFriends(this.currentUserId).subscribe({
      next: (friends: FriendDto[]) => {
        this.friends = friends;
        const prevCounts = new Map(this.contacts.map(c => [c.id, c.unreadCount]));
        this.contacts = friends.map(f => ({
          id: f.id,
          nom: f.nom,
          photo: f.photo,
          unreadCount: prevCounts.get(f.id) ?? 0
        }));
        this.filteredContacts = [...this.contacts];
        this.contacts.forEach(c => this.checkUnread(c));
      },
      error: (err) => {
        console.error('[ChatPanel] Erreur chargement amis:', err);
        this.contacts = [];
        this.filteredContacts = [];
      }
    });
  }

  // ===== CHARGER LES GROUPES =====
  loadGroups(): void {
    this.groupChatService.getGroupsForUser(this.currentUserId).subscribe({
      next: (groups) => {
        this.groups = groups;
        this.filteredGroups = [...groups];
      },
      error: (err) => console.error('[ChatPanel] Erreur chargement groupes:', err)
    });
  }

  // ===== RECHERCHE =====
  filterContacts(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredContacts = q
      ? this.contacts.filter(c => c.nom.toLowerCase().includes(q))
      : [...this.contacts];
  }

  filterGroups(): void {
    const q = this.groupSearchQuery.toLowerCase().trim();
    this.filteredGroups = q
      ? this.groups.filter(g => g.name.toLowerCase().includes(q))
      : [...this.groups];
  }

  // ===== CHECK UNREAD (1-1) =====
  private checkUnread(contact: ChatContact): void {
    this.chatService.getUnreadCount(contact.id).subscribe({
      next: (res) => {
        const prevCount = contact.unreadCount;
        contact.unreadCount = res.count;
        if (res.count > 0) {
          const windowExists = this.openChats.some(oc => oc.contact.id === contact.id);
          if (!windowExists) this.autoOpenMinimized(contact);
          if (res.count > prevCount) {
            this.showNotification(contact, res.count);
            // 🔔 Son de notification "nouveau message" (style Messenger)
            this.sound.message();
          }
        }
      },
      error: () => {}
    });
  }

  private checkSeen(chat: OpenChat): void {
    this.chatService.getLastSeenId(chat.contact.id).subscribe({
      next: (res) => { chat.lastSeenId = res.lastSeenId; },
      error: () => {}
    });
  }

  private autoOpenMinimized(contact: ChatContact): void {
    if (this.totalOpenWindows() >= 3) return;
    const chatEntry: OpenChat = {
      contact, messages: [], input: '', isMinimized: true, lastSeenId: -1
    };
    const msgSub = this.chatService.getMessages$(contact.id).subscribe(msgs => {
      chatEntry.messages = msgs;
      this.shouldScroll = true;
    });
    chatEntry.msgsSub = msgSub;
    this.subs.push(msgSub);
    this.openChats.push(chatEntry);
    this.chatService.loadHistory(contact.id);
    this.checkSeen(chatEntry);
  }

  // ===== NOTIFICATION TOAST =====
  private showNotification(contact: ChatContact, count: number): void {
    this.notification = {
      contactName: contact.nom,
      contactId:   contact.id,
      preview:     `${count} nouveau${count > 1 ? 'x' : ''} message${count > 1 ? 's' : ''}`
    };
    if (this.notifTimer) clearTimeout(this.notifTimer);
    this.notifTimer = setTimeout(() => { this.notification = null; }, 6000);
  }

  dismissNotif(): void {
    this.notification = null;
    if (this.notifTimer) clearTimeout(this.notifTimer);
  }

  openChatFromNotif(contactId: string): void {
    const contact = this.contacts.find(c => c.id === contactId);
    if (contact) this.openChat(contact);
    this.dismissNotif();
  }

  // ===== OUVRIR CHAT 1-1 =====
  openChat(contact: ChatContact): void {
    const existing = this.openChats.find(c => c.contact.id === contact.id);
    if (existing) {
      existing.isMinimized = false;
      this.markRead(existing);
      this.shouldScroll = true;
      return;
    }
    if (this.totalOpenWindows() >= 3) {
      const removed = this.openChats.shift();
      removed?.msgsSub?.unsubscribe();
    }
    const chatEntry: OpenChat = {
      contact, messages: [], input: '', isMinimized: false, lastSeenId: -1
    };
    const msgSub = this.chatService.getMessages$(contact.id).subscribe(msgs => {
      const prevLen = chatEntry.messages.length;
      chatEntry.messages = msgs;
      // Son "nouveau message" si un msg est arrivé alors que la fenêtre est déjà ouverte
      // (on ne l'utilise PAS à l'ouverture initiale, seulement sur les messages reçus par la suite).
      if (prevLen > 0 && msgs.length > prevLen) {
        const lastMsg: any = msgs[msgs.length - 1];
        const fromOther = lastMsg
          && (lastMsg.senderId || lastMsg.from) !== this.currentUserId
          && lastMsg.senderId !== this.currentUserId;
        if (fromOther) this.sound.message();
      }
      this.shouldScroll = true;
    });
    chatEntry.msgsSub = msgSub;
    this.subs.push(msgSub);
    this.openChats.push(chatEntry);
    this.chatService.loadHistory(contact.id);
    this.checkSeen(chatEntry);
    contact.unreadCount = 0;
  }

  closeChat(index: number): void {
    const removed = this.openChats.splice(index, 1)[0];
    removed?.msgsSub?.unsubscribe();
  }

  toggleMinimize(index: number): void {
    const chat = this.openChats[index];
    chat.isMinimized = !chat.isMinimized;
    if (!chat.isMinimized) {
      this.markRead(chat);
      this.shouldScroll = true;
    }
  }

  sendMessage(index: number): void {
    const chat = this.openChats[index];
    const raw = chat.input.trim();
    if (!raw) return;
    const content = this.badWords.filter(raw);
    chat.input = '';
    this.chatService.send(chat.contact.id, content);
    this.shouldScroll = true;
  }

  onEnter(event: KeyboardEvent, index: number): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage(index);
    }
  }

  private markRead(chat: OpenChat): void {
    chat.contact.unreadCount = 0;
    this.chatService.markRead(chat.contact.id);
  }

  // ===== OUVRIR GROUPE CHAT =====
  openGroupChat(group: ChatGroup): void {
    const existing = this.openGroupChats.find(gc => gc.group.id === group.id);
    if (existing) {
      existing.isMinimized = false;
      existing.unreadCount = 0;
      this.shouldScroll = true;
      return;
    }
    if (this.totalOpenWindows() >= 3) {
      // Fermer la plus ancienne fenêtre
      if (this.openGroupChats.length > 0) {
        const removed = this.openGroupChats.shift();
        removed?.msgsSub?.unsubscribe();
      } else {
        const removed = this.openChats.shift();
        removed?.msgsSub?.unsubscribe();
      }
    }
    const groupChat: OpenGroupChat = {
      group, messages: [], input: '', isMinimized: false, unreadCount: 0
    };
    const msgSub = this.groupChatService.getMessages$(group.id).subscribe(msgs => {
      const prevLen = groupChat.messages.length;
      groupChat.messages = msgs;
      if (msgs.length > prevLen) {
        // Un ou plusieurs nouveaux messages : jouer le son uniquement si le message
        // n'est pas envoyé par l'utilisateur courant (pour éviter de "ping" soi-même).
        const lastMsg: any = msgs[msgs.length - 1];
        const fromOther = lastMsg && lastMsg.senderId && lastMsg.senderId !== this.currentUserId;
        if (fromOther && prevLen > 0) {
          this.sound.message();
        }
        if (groupChat.isMinimized) {
          groupChat.unreadCount += msgs.length - prevLen;
        }
      }
      this.shouldScroll = true;
    });
    groupChat.msgsSub = msgSub;
    this.subs.push(msgSub);
    this.openGroupChats.push(groupChat);
    this.groupChatService.loadHistory(group.id);
  }

  closeGroupChat(index: number): void {
    const removed = this.openGroupChats.splice(index, 1)[0];
    removed?.msgsSub?.unsubscribe();
  }

  toggleGroupMinimize(index: number): void {
    const gc = this.openGroupChats[index];
    gc.isMinimized = !gc.isMinimized;
    if (!gc.isMinimized) {
      gc.unreadCount = 0;
      this.shouldScroll = true;
    }
  }

  sendGroupMessage(index: number): void {
    const gc = this.openGroupChats[index];
    const raw = gc.input.trim();
    if (!raw) return;
    const content = this.badWords.filter(raw);
    gc.input = '';
    this.groupChatService.send(
      gc.group.id, content, this.currentUserName, this.currentUserPhoto
    );
    this.shouldScroll = true;
  }

  onGroupEnter(event: KeyboardEvent, index: number): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendGroupMessage(index);
    }
  }

  // ===== MODAL CRÉATION GROUPE =====
  openCreateGroupModal(): void {
    this.showCreateGroupModal = true;
    this.newGroupName = '';
    this.selectedFriendIds = new Set();
  }

  closeCreateGroupModal(): void {
    this.showCreateGroupModal = false;
  }

  toggleFriendSelection(friendId: string): void {
    if (this.selectedFriendIds.has(friendId)) {
      this.selectedFriendIds.delete(friendId);
    } else {
      this.selectedFriendIds.add(friendId);
    }
  }

  isFriendSelected(friendId: string): boolean {
    return this.selectedFriendIds.has(friendId);
  }

  canCreateGroup(): boolean {
    return this.newGroupName.trim().length >= 2 && this.selectedFriendIds.size >= 1;
  }

  createGroup(): void {
    if (!this.canCreateGroup()) return;
    const memberIds = Array.from(this.selectedFriendIds);
    this.groupChatService.createGroup(
      this.newGroupName.trim(), this.currentUserId, memberIds
    ).subscribe({
      next: (group) => {
        this.groups.push(group);
        this.filteredGroups = [...this.groups];
        this.closeCreateGroupModal();
        this.openGroupChat(group);
        this.activeTab = 'groups';
      },
      error: (err) => console.error('[ChatPanel] Erreur création groupe:', err)
    });
  }

  // ===== GESTION MEMBRES GROUPE =====

  showActionMsg(text: string, isError = false): void {
    this.actionMsg = { text, isError };
    if (this.actionMsgTimer) clearTimeout(this.actionMsgTimer);
    this.actionMsgTimer = setTimeout(() => { this.actionMsg = null; }, 3500);
  }

  private playRingtone(): void {
    try {
      this.ringtone.loop = true;
      this.ringtone.volume = 0.7;
      this.ringtone.currentTime = 0;
      this.ringtone.play().catch(() => {});
    } catch (_) {}
  }

  private stopRingtone(): void {
    try {
      this.ringtone.pause();
      this.ringtone.currentTime = 0;
    } catch (_) {}
  }

  isGroupCreator(gc: OpenGroupChat): boolean {
    return (gc.group.creatorId || '').trim() === (this.currentUserId || '').trim();
  }

  toggleMembersPanel(gc: OpenGroupChat): void {
    gc.showMembers = !gc.showMembers;
    if (gc.showMembers) {
      this.resolveGroupMembers(gc.group);
    }
  }

  resolveGroupMembers(group: ChatGroup): void {
    const allIds = [
      ...(group.memberIds || []),
      ...(group.blockedMemberIds || [])
    ];
    allIds.forEach(id => {
      if (!this.memberNames.has(id)) {
        this.userService.getById(id).subscribe({
          next: (u) => {
            this.memberNames.set(id, u.nom || id);
            this.memberPhotos.set(id, u.photo || '');
          },
          error: () => this.memberNames.set(id, id)
        });
      }
    });
  }

  getMemberName(userId: string): string {
    return this.memberNames.get(userId) || userId;
  }

  getMemberPhoto(userId: string): string {
    return this.memberPhotos.get(userId) || '';
  }

  kickMember(gc: OpenGroupChat, userId: string): void {
    const name = this.getMemberName(userId);
    if (!confirm(`Exclure ${name} du groupe ?`)) return;
    this.groupChatService.kickMember(gc.group.id, userId, this.currentUserId).subscribe({
      next: (updated) => {
        gc.group = { ...updated };
        gc.messages = gc.messages.filter(m => m.senderId !== userId);
        this.showActionMsg(`✅ ${name} a été exclu du groupe`);
      },
      error: (err) => {
        console.error('[ChatPanel] Erreur kick:', err);
        const msg = err?.error?.error || 'Impossible d\'exclure ce membre';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  blockGroupMember(gc: OpenGroupChat, userId: string): void {
    const name = this.getMemberName(userId);
    if (!confirm(`Bloquer ${name} ? Il sera exclu et ne pourra plus rejoindre ce groupe.`)) return;
    this.groupChatService.blockMember(gc.group.id, userId, this.currentUserId).subscribe({
      next: (updated) => {
        gc.group = { ...updated };
        this.showActionMsg(`🚫 ${name} a été bloqué dans ce groupe`);
      },
      error: (err) => {
        console.error('[ChatPanel] Erreur block groupe:', err);
        const msg = err?.error?.error || 'Impossible de bloquer ce membre';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  unblockGroupMember(gc: OpenGroupChat, userId: string): void {
    const name = this.getMemberName(userId);
    this.groupChatService.unblockMember(gc.group.id, userId, this.currentUserId).subscribe({
      next: (updated) => {
        gc.group = { ...updated };
        this.showActionMsg(`✅ ${name} a été débloqué`);
      },
      error: (err) => {
        console.error('[ChatPanel] Erreur unblock groupe:', err);
        this.showActionMsg(`❌ Impossible de débloquer ce membre`, true);
      }
    });
  }

  isMemberBlocked(gc: OpenGroupChat, userId: string): boolean {
    return (gc.group.blockedMemberIds || []).includes(userId);
  }

  // ===== BLOQUER UN AMI (CHAT 1-1) =====

  loadBlockedContacts(): void {
    this.friendService.getBlockedUsers(this.currentUserId).subscribe({
      next: (blocked) => {
        this.blockedContactIds = new Set(blocked.map(b => b.id));
        this.blockedContacts = blocked;
      },
      error: () => {}
    });
  }

  isContactBlocked(contactId: string): boolean {
    return this.blockedContactIds.has(contactId);
  }

  blockContact(contact: ChatContact): void {
    if (!confirm(`Bloquer ${contact.nom} ? Vous ne pourrez plus vous envoyer de messages.`)) return;
    this.friendService.blockUser(this.currentUserId, contact.id).subscribe({
      next: () => {
        this.blockedContactIds.add(contact.id);
        // Ajouter à la liste complète des bloqués si pas déjà présent
        if (!this.blockedContacts.find(b => b.id === contact.id)) {
          this.blockedContacts.push({ id: contact.id, nom: contact.nom, photo: contact.photo, status: 'BLOCKED' });
        }
        this.contacts = this.contacts.filter(c => c.id !== contact.id);
        this.filteredContacts = this.filteredContacts.filter(c => c.id !== contact.id);
        this.showActionMsg(`🚫 ${contact.nom} a été bloqué`);
      },
      error: (err) => {
        console.error('[ChatPanel] Erreur blocage ami:', err);
        const msg = err?.error?.error || 'Impossible de bloquer cet utilisateur';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  unblockContact(chat: OpenChat): void {
    this.friendService.unblockUser(this.currentUserId, chat.contact.id).subscribe({
      next: () => {
        this.blockedContactIds.delete(chat.contact.id);
        this.blockedContacts = this.blockedContacts.filter(b => b.id !== chat.contact.id);
        this.showActionMsg(`✅ ${chat.contact.nom} a été débloqué`);
        this.loadFriendsAsContacts();
      },
      error: (err) => {
        const msg = err?.error?.error || 'Impossible de débloquer';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  // Débloquer depuis le panneau bloqués (sidebar)
  unblockFromList(blocked: FriendDto): void {
    this.friendService.unblockUser(this.currentUserId, blocked.id).subscribe({
      next: () => {
        this.blockedContactIds.delete(blocked.id);
        this.blockedContacts = this.blockedContacts.filter(b => b.id !== blocked.id);
        this.showActionMsg(`✅ ${blocked.nom} a été débloqué`);
        this.loadFriendsAsContacts();
      },
      error: (err) => {
        const msg = err?.error?.error || 'Impossible de débloquer';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  // Re-envoyer une invitation d'ami après déblocage
  reinviteUser(blocked: FriendDto): void {
    this.friendService.unblockUser(this.currentUserId, blocked.id).subscribe({
      next: () => {
        this.blockedContactIds.delete(blocked.id);
        this.blockedContacts = this.blockedContacts.filter(b => b.id !== blocked.id);
        // Re-envoyer la demande d'ami
        this.friendService.sendRequest(this.currentUserId, blocked.id).subscribe({
          next: () => this.showActionMsg(`📨 Invitation renvoyée à ${blocked.nom}`),
          error: () => this.showActionMsg(`✅ ${blocked.nom} débloqué (invitation déjà existante)`)
        });
        this.loadFriendsAsContacts();
      },
      error: (err) => {
        const msg = err?.error?.error || 'Erreur';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  // ===== AJOUTER MEMBRES AU GROUPE =====

  openAddMembersPanel(gc: OpenGroupChat): void {
    this.showAddMembersPanel = true;
    this.selectedFriendsToAdd = new Set();
    // Amis qui ne sont pas déjà membres
    this.friendsNotInGroup = this.friends.filter(
      f => !(gc.group.memberIds || []).includes(f.id)
        && !(gc.group.blockedMemberIds || []).includes(f.id)
    );
  }

  closeAddMembersPanel(): void {
    this.showAddMembersPanel = false;
    this.selectedFriendsToAdd = new Set();
  }

  toggleFriendToAdd(id: string): void {
    if (this.selectedFriendsToAdd.has(id)) this.selectedFriendsToAdd.delete(id);
    else this.selectedFriendsToAdd.add(id);
  }

  addMembersToGroup(gc: OpenGroupChat): void {
    if (this.selectedFriendsToAdd.size === 0) return;
    const toAdd = Array.from(this.selectedFriendsToAdd);
    this.groupChatService.addMembers(gc.group.id, toAdd).subscribe({
      next: (updated) => {
        gc.group = { ...updated };
        // Résoudre les noms des nouveaux membres ajoutés
        this.resolveGroupMembers(gc.group);
        this.closeAddMembersPanel();
        this.showActionMsg(`✅ ${toAdd.length} membre(s) ajouté(s)`);
      },
      error: (err) => {
        const msg = err?.error?.error || 'Impossible d\'ajouter les membres';
        this.showActionMsg(`❌ ${msg}`, true);
      }
    });
  }

  // ===== APPELS VOCAUX AGORA =====

  // Génère un nom de canal court (max 64 chars) compatible Agora
  private makeChannelName(idA: string, idB: string): string {
    // Prendre les 8 derniers caractères de chaque UUID (sans tirets) + timestamp court
    const a = idA.replace(/-/g, '').slice(-8);
    const b = idB.replace(/-/g, '').slice(-8);
    const t = (Date.now() % 100000).toString();
    return `cv_${a}_${b}_${t}`; // ex: cv_1ef2528c_cb7f1ef2_12345 → 30 chars max
  }

  async startCall(chat: OpenChat): Promise<void> {
    console.log('[Call] Starting call to:', chat.contact.id, chat.contact.nom);
    console.log('[Call] Caller:', this.currentUserId, this.currentUserName);
    try {
      // 1. Demander au backend le canal + token Agora, et notifier l'autre via WebSocket
      const res = await fetch('http://localhost:8083/api/calls/initiate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          callerId:    this.currentUserId,
          callerName:  this.currentUserName,
          callerPhoto: this.currentUserPhoto,
          calleeId:    chat.contact.id
        })
      });
      const data = await res.json();
      const channelName: string = data.channelName;
      const token: string | null = data.token || null;

      // 2. Rejoindre le canal Agora avec le token reçu
      await this.callService.joinChannel(this.AGORA_APP_ID, channelName, token, 0);
      this.activeCall = {
        channelName,
        contactName: chat.contact.nom,
        contactId: chat.contact.id,
        isMuted: false
      };
      this.showActionMsg(`📞 En attente de ${chat.contact.nom}...`);
    } catch (err: any) {
      console.error('[Call] Erreur startCall:', err);
      const reason = err?.message || err?.code || String(err);
      if (reason.toLowerCase().includes('permission') || reason.toLowerCase().includes('notallowed')) {
        this.showActionMsg('❌ Microphone bloqué — clique sur 🔒 dans Chrome et autorise le micro', true);
      } else {
        this.showActionMsg(`❌ Erreur appel : ${reason}`, true);
      }
    }
  }

  async startGroupCall(gc: OpenGroupChat): Promise<void> {
    try {
      const memberIds = gc.group.memberIds || [];
      if (memberIds.length < 2) {
        this.showActionMsg('❌ Le groupe doit avoir au moins 2 membres pour appeler', true);
        return;
      }
      const res = await fetch('http://localhost:8083/api/calls/initiate-group', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          callerId:    this.currentUserId,
          callerName:  this.currentUserName,
          callerPhoto: this.currentUserPhoto,
          groupName:   gc.group.name,
          memberIds:   memberIds
        })
      });
      const data = await res.json();
      const channelName: string  = data.channelName;
      const token: string | null = data.token || null;

      await this.callService.joinChannel(this.AGORA_APP_ID, channelName, token, 0);
      this.activeCall = {
        channelName,
        contactName: gc.group.name,
        contactId:   String(gc.group.id),
        isMuted: false
      };
      this.showActionMsg(`📞 Appel de groupe lancé dans "${gc.group.name}"...`);
    } catch (err: any) {
      console.error('[GroupCall] Erreur:', err);
      const reason = err?.message || String(err);
      if (reason.toLowerCase().includes('permission') || reason.toLowerCase().includes('notallowed')) {
        this.showActionMsg('❌ Microphone bloqué — autorise le micro dans Chrome (🔒)', true);
      } else {
        this.showActionMsg(`❌ Erreur appel de groupe : ${reason}`, true);
      }
    }
  }

  async acceptCall(): Promise<void> {
    if (!this.incomingCall) return;
    const { channelName, callerName, callerId, token } = this.incomingCall;
    this.stopRingtone();
    // Rejoindre le même canal Agora que le caller (avec le même token)
    try {
      await this.callService.joinChannel(this.AGORA_APP_ID, channelName, token, 0);
      // On efface l'appel entrant SEULEMENT si join réussit
      this.incomingCall = null;
      this.activeCall = {
        channelName,
        contactName: callerName,
        contactId: callerId,
        isMuted: false
      };
      this.showActionMsg(`📞 Appel accepté`);
    } catch (err: any) {
      console.error('[Call] Erreur joinChannel callee:', err);
      const reason = err?.message || err?.code || JSON.stringify(err) || 'erreur inconnue';
      // Laisser incomingCall intact pour que l'utilisateur puisse refuser manuellement
      if (reason.toLowerCase().includes('permission') || reason.toLowerCase().includes('denied') || reason.toLowerCase().includes('notallowed')) {
        this.showActionMsg('❌ Microphone bloqué — Autorise le micro dans Chrome (🔒 à gauche de l\'URL)', true);
      } else {
        this.showActionMsg(`❌ Erreur appel : ${reason}`, true);
      }
    }
  }

  rejectCall(): void {
    if (!this.incomingCall) return;
    fetch('http://localhost:8083/api/calls/reject', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        callerId: this.incomingCall.callerId,
        calleeId: this.currentUserId
      })
    }).catch(() => {});
    this.incomingCall = null;
    this.stopRingtone();
  }

  async endCall(): Promise<void> {
    if (!this.activeCall) return;
    // Notifier l'autre que l'appel est terminé
    fetch('http://localhost:8083/api/calls/end', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ otherId: this.activeCall.contactId, enderId: this.currentUserId })
    }).catch(() => {});
    // Quitter le canal Agora
    await this.callService.leaveChannel();
    this.activeCall = null;
    this.showActionMsg('📞 Appel terminé');
  }

  async toggleMute(): Promise<void> {
    await this.callService.toggleMute();
    // Synchroniser l'état local avec l'état Agora
    if (this.activeCall && this.callService.state) {
      this.activeCall.isMuted = this.callService.state.isMuted;
    }
  }

  async endCallLocally(): Promise<void> {
    await this.callService.leaveChannel();
    this.activeCall = null;
  }

  // ===== COULEURS SENDERS GROUPE =====
  getSenderColor(senderId: string): string {
    if (senderId === this.currentUserId) return '#E85D24';
    if (!this.colorMap.has(senderId)) {
      const idx = this.colorMap.size % (this.senderColors.length - 1) + 1;
      this.colorMap.set(senderId, this.senderColors[idx]);
    }
    return this.colorMap.get(senderId)!;
  }

  // ===== HELPERS =====
  private totalOpenWindows(): number {
    return this.openChats.length + this.openGroupChats.length;
  }

  private scrollAllToBottom(): void {
    this.msgLists?.forEach(ref => {
      try { ref.nativeElement.scrollTop = ref.nativeElement.scrollHeight; } catch {}
    });
  }

  getImageUrl(url?: string): string {
    if (!url) return '';
    return url.startsWith('http') ? url : 'http://localhost:8081' + url;
  }

  getInitials(nom: string): string {
    return nom ? nom.charAt(0).toUpperCase() : '?';
  }

  formatTime(sentAt: string): string {
    if (!sentAt) return '';
    try {
      return new Date(sentAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    } catch { return ''; }
  }

  totalUnread(): number {
    return this.contacts.reduce((s, c) => s + (c.unreadCount || 0), 0);
  }

  totalGroupUnread(): number {
    return this.openGroupChats.reduce((s, gc) => s + (gc.unreadCount || 0), 0);
  }

  getGroupMemberCount(group: ChatGroup): number {
    return group.memberIds?.length ?? 0;
  }
}
