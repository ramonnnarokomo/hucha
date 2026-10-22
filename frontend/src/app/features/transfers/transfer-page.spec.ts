import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Account, Payee, Transfer } from '../../core/models';
import { TransferPage } from './transfer-page';

const checking: Account = {
  id: 'acc-1',
  iban: 'ES8899990001403181129555',
  type: 'CHECKING',
  name: 'Cuenta principal',
  status: 'ACTIVE',
  balance: 500,
  currency: 'EUR',
  createdAt: '2026-05-01T10:00:00Z',
};

const marcos: Payee = {
  id: 'payee-1',
  name: 'Marcos Ruiz',
  iban: 'ES5599990001422844409586',
  internal: true,
  createdAt: '2026-05-01T10:00:00Z',
};

const pending: Transfer = {
  id: 'tr-1',
  fromAccountId: 'acc-1',
  toIban: marcos.iban,
  beneficiaryName: marcos.name,
  amount: 45.5,
  status: 'PENDING_SCA',
  internal: true,
  scaRequired: true,
  scaExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
  createdAt: new Date().toISOString(),
};

describe('TransferPage', () => {
  let fixture: ComponentFixture<TransferPage>;
  let backend: HttpTestingController;
  let page: HTMLElement;

  const click = (selector: string, text?: string) => {
    const buttons = Array.from(page.querySelectorAll<HTMLElement>(selector));
    const target = text ? buttons.find((b) => b.textContent?.includes(text)) : buttons[0];
    if (!target) throw new Error(`No encuentro ${selector} ${text ?? ''}`);
    target.click();
    fixture.detectChanges();
  };

  const type = (selector: string, value: string) => {
    const input = page.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TransferPage);
    page = fixture.nativeElement;
    fixture.detectChanges();
    backend.expectOne('/api/accounts').flush([checking]);
    backend.expectOne('/api/payees').flush([marcos]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('envía con código de verificación y reutiliza la Idempotency-Key al reintentar', async () => {
    // 1. Destinatario guardado
    click('button.list-item', 'Marcos');
    expect(page.textContent).toContain('¿Cuánto quieres enviar?');

    // 2. Importe
    type('#amount', '45,50');
    click('button[type=submit]', 'Revisar');
    expect(page.textContent).toContain('Revisa la transferencia');

    // 3. Primer envío: se corta la red
    click('button.btn-primary', 'Enviar');
    const first = backend.expectOne('/api/transfers');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    expect(first.request.body).toEqual({
      fromAccountId: 'acc-1',
      toIban: marcos.iban,
      beneficiaryName: 'Marcos Ruiz',
      amount: 45.5,
      concept: undefined,
    });
    first.error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(page.textContent).toContain('No hay conexión con el servidor.');

    // Reintento: misma clave, así el backend no puede cobrar dos veces
    click('button.btn-primary', 'Reintentar');
    const retry = backend.expectOne('/api/transfers');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(pending, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    // 4. SCA: al escribir el sexto dígito se confirma solo
    expect(page.textContent).toContain('Introduce el código de verificación');
    type('input[aria-label="Código de 6 dígitos"]', '123456');
    const confirm = backend.expectOne('/api/transfers/tr-1/confirm');
    expect(confirm.request.body).toEqual({ code: '123456' });
    confirm.flush({ ...pending, status: 'COMPLETED', completedAt: new Date().toISOString() });
    fixture.detectChanges();

    expect(page.textContent).toContain('Enviada');
    expect(page.textContent).toContain('Marcos Ruiz');
  });

  it('avisa de que hará falta código antes de enviar a alguien nuevo', () => {
    type('#iban', 'ES9121000418450200051332');
    type('#name', 'Ana Martín');
    click('form.new-recipient button[type=submit]');
    backend.match('/api/payees/check-iban?iban=ES9121000418450200051332').forEach((r) =>
      r.flush({
        iban: 'ES9121000418450200051332',
        valid: true,
        internal: false,
        savedPayee: false,
      }),
    );

    type('#amount', '20');
    expect(page.textContent).not.toContain('código de verificación');
    type('#amount', '45');
    expect(page.textContent).toContain('código de verificación');
  });

  it('no deja revisar si no hay saldo suficiente', () => {
    click('button.list-item', 'Marcos');
    type('#amount', '600');
    expect(page.textContent).toContain('No tienes saldo suficiente');
    const review = Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Revisar'),
    )!;
    expect(review.disabled).toBe(true);
  });
});
