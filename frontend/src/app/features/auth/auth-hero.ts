import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-auth-hero',
  imports: [Icon],
  template: `
    <div class="brand">
      <span class="logo"><app-icon name="piggy" [size]="24" [stroke]="2" /></span> Hucha
    </div>
    <div>
      <h1>Tu dinero, claro y en tiempo real.</h1>
      <p>
        Cuenta con IBAN, tarjeta virtual, huchas de ahorro y transferencias al instante entre
        clientes.
      </p>
    </div>
    <ul class="features">
      <li>
        <app-icon name="shield" />
        <div>
          <strong>Verificación por código</strong
          ><span>Las transferencias sensibles se confirman con un código de un solo uso.</span>
        </div>
      </li>
      <li>
        <app-icon name="activity" />
        <div>
          <strong>Avisos al instante</strong
          ><span>Notificaciones en tiempo real cuando recibes dinero o se deniega un pago.</span>
        </div>
      </li>
      <li>
        <app-icon name="chart" />
        <div>
          <strong>Analítica de gastos</strong
          ><span>Ingresos, gastos por categoría y evolución del saldo.</span>
        </div>
      </li>
    </ul>
    <p class="disclaimer">
      Proyecto de demostración. Hucha no es un banco real y no mueve dinero de verdad.
    </p>
  `,
  styleUrl: './auth-hero.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthHero {}
