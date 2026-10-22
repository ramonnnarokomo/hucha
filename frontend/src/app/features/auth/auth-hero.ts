import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../../shared/icon';

/**
 * Lado izquierdo del login: una hoja de libro mayor con un asiento de ejemplo. Cuenta de un
 * vistazo la idea del proyecto: cada euro se apunta dos veces y el asiento siempre cuadra.
 */
@Component({
  selector: 'app-auth-hero',
  imports: [Icon],
  template: `
    <div class="wordmark">
      <app-icon name="logo" [size]="24" [stroke]="1.7" />
      <span>Hucha</span>
    </div>

    <div class="pitch">
      <h1>Cada euro, apuntado dos&nbsp;veces.</h1>
      <p>
        Hucha es un banco de demostración montado sobre un libro mayor de doble entrada: todo
        movimiento tiene su contrapartida y el saldo siempre cuadra.
      </p>
    </div>

    <figure class="ledger" aria-label="Ejemplo de asiento contable de una transferencia">
      <div class="ledger-head">
        <span>Asiento nº 0001 · Transferencia</span>
        <span>07/10/2026</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>Cuenta</th>
            <th class="right">Debe</th>
            <th class="right">Haber</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Lucía F. <span class="iban">••••&nbsp;7889</span></td>
            <td class="right">45,50</td>
            <td class="right"></td>
          </tr>
          <tr>
            <td>Marcos R. <span class="iban">••••&nbsp;9586</span></td>
            <td class="right"></td>
            <td class="right">45,50</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td>Suma</td>
            <td class="right"><span class="sum">45,50</span></td>
            <td class="right"><span class="sum">45,50</span></td>
          </tr>
        </tfoot>
      </table>
    </figure>

    <ul class="facts">
      <li>
        <strong>Código de un solo uso</strong> para las transferencias grandes o a alguien nuevo.
      </li>
      <li><strong>Sin cobros dobles</strong> aunque se corte la red y repitas la petición.</li>
      <li><strong>Avisos al momento</strong> cuando te llega dinero o se deniega un pago.</li>
    </ul>

    <p class="disclaimer">Proyecto de demostración. No es un banco y no mueve dinero de verdad.</p>
  `,
  styleUrl: './auth-hero.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthHero {}
