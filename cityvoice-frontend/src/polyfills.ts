import { Buffer } from 'buffer';

(window as any).global = window;
(window as any).process = { env: {} };
(window as any).Buffer = Buffer;
