import { describeDevice } from './device';

describe('describeDevice', () => {
  it('resume los User-Agent habituales', () => {
    expect(
      describeDevice(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36',
      ),
    ).toEqual({ label: 'Chrome en Windows', mobile: false });
    expect(
      describeDevice(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
      ),
    ).toEqual({ label: 'Safari en iOS', mobile: true });
    expect(
      describeDevice('Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0')
        .label,
    ).toBe('Firefox en Linux');
    expect(describeDevice('curl/8.5.0').label).toBe('curl');
    expect(describeDevice(null).label).toBe('Dispositivo desconocido');
  });
});
