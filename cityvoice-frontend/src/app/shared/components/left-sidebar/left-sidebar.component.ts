import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { UserService, UserDto } from '../../../core/services/user.service';
import { FriendService, FriendDto } from '../../../core/services/friend.service';

@Component({
  selector: 'app-left-sidebar',
  templateUrl: './left-sidebar.component.html',
  styleUrls: ['./left-sidebar.component.css']
})
export class LeftSidebarComponent implements OnInit, OnDestroy {

  user: UserDto | null = null;
  currentRoute = '';
  pendingFriendCount = 0;

  // ── Utilisateurs bloqués ─────────────────────────────────
  blockedUsers: FriendDto[] = [];
  showBlockedList = false;
  blockedLoading = false;
  unblockingId: string | null = null;

  private currentUserId = '';
  private subs: Subscription[] = [];

  constructor(
    private authService: AuthService,
    private userService: UserService,
    private friendService: FriendService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const currentUser = this.authService.getCurrentUser();
    if (currentUser?.userId) {
      this.currentUserId = currentUser.userId;
      this.userService.getById(currentUser.userId).subscribe(u => { this.user = u; });
      this.loadPendingCount(currentUser.userId);

      // Rafraîchir le badge demandes d'amis toutes les 15 secondes
      const pollSub = interval(15000).subscribe(() => {
        this.loadPendingCount(currentUser.userId);
      });
      this.subs.push(pollSub);
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  private loadPendingCount(userId: string): void {
    this.friendService.getPendingCount(userId).subscribe({
      next: (res) => { this.pendingFriendCount = res.count; },
      error: () => {}
    });
  }

  // ── Gestion liste bloqués ────────────────────────────────

  toggleBlockedList(): void {
    this.showBlockedList = !this.showBlockedList;
    if (this.showBlockedList && this.blockedUsers.length === 0) {
      this.loadBlockedUsers();
    }
  }

  private loadBlockedUsers(): void {
    if (!this.currentUserId) return;
    this.blockedLoading = true;
    this.friendService.getBlockedUsers(this.currentUserId).subscribe({
      next: (users) => { this.blockedUsers = users; this.blockedLoading = false; },
      error: () => { this.blockedLoading = false; }
    });
  }

  unblockUser(user: FriendDto): void {
    if (!this.currentUserId || this.unblockingId) return;
    this.unblockingId = user.id;
    this.friendService.unblockUser(this.currentUserId, user.id).subscribe({
      next: () => {
        this.blockedUsers = this.blockedUsers.filter(u => u.id !== user.id);
        this.unblockingId = null;
      },
      error: () => { this.unblockingId = null; }
    });
  }

  // ── Helpers ──────────────────────────────────────────────

  isLoggedIn(): boolean { return this.authService.isLoggedIn(); }

  getImageUrl(url: string): string {
    if (!url) return '';
    return url.startsWith('http') ? url : 'http://localhost:8081' + url;
  }

  getInitials(): string {
    return this.user?.nom ? this.user.nom.charAt(0).toUpperCase() : 'U';
  }

  navigateTo(path: string): void { this.router.navigate([path]); }

  isActive(path: string): boolean { return this.router.url.startsWith(path); }
}
