import { HttpErrorResponse } from '@angular/common/http';

/** Error de la API (ProblemDetail, RFC 9457) reducido a lo que la interfaz necesita. */
export interface ApiError {
  status: number;
  code?: string;
  message: string;
  fieldErrors: Record<string, string>;
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    const body = (error.error ?? {}) as {
      code?: string;
      detail?: string;
      errors?: Record<string, string>;
    };
    if (error.status === 0) {
      return { status: 0, message: 'No hay conexión con el servidor.', fieldErrors: {} };
    }
    return {
      status: error.status,
      code: body.code,
      message: body.detail ?? 'Ha ocurrido un error inesperado.',
      fieldErrors: body.errors ?? {},
    };
  }
  return { status: -1, message: 'Ha ocurrido un error inesperado.', fieldErrors: {} };
}
