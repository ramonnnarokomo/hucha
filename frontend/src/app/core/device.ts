/** Resume un User-Agent en algo legible: "Chrome en Windows". Basta para reconocer tus sesiones. */
export function describeDevice(userAgent: string | null | undefined): {
  label: string;
  mobile: boolean;
} {
  const ua = userAgent ?? '';
  if (!ua) {
    return { label: 'Dispositivo desconocido', mobile: false };
  }
  const browser = /Edg\//.test(ua)
    ? 'Edge'
    : /OPR\//.test(ua)
      ? 'Opera'
      : /Firefox\//.test(ua)
        ? 'Firefox'
        : /Chrome\//.test(ua)
          ? 'Chrome'
          : /Safari\//.test(ua)
            ? 'Safari'
            : /curl\//.test(ua)
              ? 'curl'
              : null;
  const os = /Windows/.test(ua)
    ? 'Windows'
    : /Android/.test(ua)
      ? 'Android'
      : /iPhone|iPad|iOS/.test(ua)
        ? 'iOS'
        : /Mac OS X|Macintosh/.test(ua)
          ? 'macOS'
          : /Linux/.test(ua)
            ? 'Linux'
            : null;
  const mobile = /Mobile|Android|iPhone|iPad/.test(ua);
  if (browser && os) return { label: `${browser} en ${os}`, mobile };
  return { label: browser ?? os ?? 'Otro cliente', mobile };
}
