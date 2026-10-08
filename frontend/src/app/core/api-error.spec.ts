import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './api-error';

describe('toApiError', () => {
  it('lee el ProblemDetail del backend con su código y errores por campo', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        code: 'INSUFFICIENT_FUNDS',
        detail: 'No tienes saldo suficiente.',
        errors: { amount: 'Demasiado alto' },
      },
    });
    expect(toApiError(error)).toEqual({
      status: 422,
      code: 'INSUFFICIENT_FUNDS',
      message: 'No tienes saldo suficiente.',
      fieldErrors: { amount: 'Demasiado alto' },
    });
  });

  it('distingue la falta de conexión', () => {
    const error = new HttpErrorResponse({ status: 0 });
    expect(toApiError(error).message).toBe('No hay conexión con el servidor.');
  });

  it('tiene un mensaje genérico para todo lo demás', () => {
    expect(toApiError(new Error('boom')).status).toBe(-1);
    expect(toApiError(new HttpErrorResponse({ status: 500 })).message).toBe(
      'Ha ocurrido un error inesperado.',
    );
  });
});
