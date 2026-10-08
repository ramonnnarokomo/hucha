import { passwordStrength } from './security-page';

describe('passwordStrength', () => {
  it('no da por buena una contraseña que el servidor rechazaría', () => {
    expect(passwordStrength('').level).toBe(0);
    expect(passwordStrength('corta1').level).toBe(1);
    expect(passwordStrength('sinnumeros').level).toBe(1);
  });

  it('premia la longitud y la variedad', () => {
    expect(passwordStrength('demo1234').level).toBe(2);
    expect(passwordStrength('demo1234demo').level).toBe(3);
    expect(passwordStrength('Demo-1234-Hucha').level).toBe(4);
  });
});
