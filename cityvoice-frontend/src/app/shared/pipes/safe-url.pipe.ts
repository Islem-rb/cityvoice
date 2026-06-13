// shared/pipes/safe-url.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({ name: 'safeUrl' })
export class SafeUrlPipe implements PipeTransform {
  constructor(private s: DomSanitizer) {}
  transform(url: string) {
    return this.s.bypassSecurityTrustResourceUrl(url);
  }
}