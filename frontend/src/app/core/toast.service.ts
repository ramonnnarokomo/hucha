import { Injectable, signal } from '@angular/core';

export type ToastKind = 'info' | 'success' | 'error';

export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private nextId = 1;

  show(title: string, message?: string, kind: ToastKind = 'info', durationMs = 5000): void {
    const toast: Toast = { id: this.nextId++, kind, title, message };
    this.toasts.update((list) => [...list.slice(-3), toast]);
    setTimeout(() => this.dismiss(toast.id), durationMs);
  }

  success(title: string, message?: string): void {
    this.show(title, message, 'success');
  }

  error(title: string, message?: string): void {
    this.show(title, message, 'error', 7000);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }
}
