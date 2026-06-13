import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../../../core/services/auth.service';
import { FriendService, FriendDto } from '../../../../core/services/friend.service';
import { UserService, UserDto } from '../../../../core/services/user.service';

@Component({
  selector: 'app-friends',
  templateUrl: './friends.component.html',
  styleUrls: ['./friends.component.css']
})
export class FriendsComponent implements OnInit {

  currentUserId = '';
  activeTab: 'friends' | 'requests' | 'find' = 'friends';

  friends: FriendDto[]    = [];
  requests: FriendDto[]   = [];
  allUsers: UserDto[]     = [];
  filteredUsers: UserDto[] = [];
  searchQuery = '';

  // Statuts pour les utilisateurs dans "Trouver des amis"
  userStatuses: Map<string, string> = new Map();

  loading = false;

  constructor(
    private auth: AuthService,
    private friendService: FriendService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    const user = this.auth.getCurrentUser();
    if (user?.userId) {
      this.currentUserId = user.userId;
      this.loadFriends();
      this.loadRequests();
    }
  }

  setTab(tab: 'friends' | 'requests' | 'find'): void {
    this.activeTab = tab;
    if (tab === 'find' && this.allUsers.length === 0) {
      this.loadAllUsers();
    }
  }

  loadFriends(): void {
    this.friendService.getFriends(this.currentUserId).subscribe({
      next: (f) => { this.friends = f; },
      error: () => { this.friends = []; }
    });
  }

  loadRequests(): void {
    this.friendService.getPendingRequests(this.currentUserId).subscribe({
      next: (r) => { this.requests = r; },
      error: () => { this.requests = []; }
    });
  }

  loadAllUsers(): void {
    this.loading = true;
    this.userService.getAll().subscribe({
      next: (users) => {
        this.allUsers = users.filter(u => u.id !== this.currentUserId);
        this.filteredUsers = [...this.allUsers];
        this.loading = false;
        // Charger les statuts
        this.allUsers.forEach(u => {
          this.friendService.getStatus(this.currentUserId, u.id).subscribe(res => {
            this.userStatuses.set(u.id, res.status);
          });
        });
      },
      error: () => { this.loading = false; }
    });
  }

  filterUsers(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredUsers = q
      ? this.allUsers.filter(u => u.nom.toLowerCase().includes(q))
      : [...this.allUsers];
  }

  sendRequest(userId: string): void {
    this.friendService.sendRequest(this.currentUserId, userId).subscribe({
      next: () => { this.userStatuses.set(userId, 'PENDING_SENT'); },
      error: () => {}
    });
  }

  accept(requesterId: string): void {
    this.friendService.acceptRequest(requesterId, this.currentUserId).subscribe({
      next: () => {
        this.requests = this.requests.filter(r => r.id !== requesterId);
        this.loadFriends();
      }
    });
  }

  reject(requesterId: string): void {
    this.friendService.rejectRequest(requesterId, this.currentUserId).subscribe({
      next: () => {
        this.requests = this.requests.filter(r => r.id !== requesterId);
      }
    });
  }

  removeFriend(friendId: string): void {
    this.friendService.removeFriend(this.currentUserId, friendId).subscribe({
      next: () => {
        this.friends = this.friends.filter(f => f.id !== friendId);
        this.userStatuses.set(friendId, 'NONE');
      }
    });
  }

  getStatus(userId: string): string {
    return this.userStatuses.get(userId) || 'NONE';
  }

  getImageUrl(url?: string): string {
    if (!url) return '';
    return url.startsWith('http') ? url : 'http://localhost:8081' + url;
  }

  getInitials(nom: string): string {
    return nom ? nom.charAt(0).toUpperCase() : '?';
  }

  getLocation(u: any): string {
    return u.ville || u.gouvernorat || '';
  }
}
