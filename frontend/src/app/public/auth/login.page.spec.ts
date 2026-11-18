import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  const login = vi.fn();

  beforeEach(() => {
    login.mockReset();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function type(el: HTMLElement, selector: string, value: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('no llama al backend si el formulario no es valido y muestra los errores', async () => {
    const { fixture, el } = render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(login).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Este campo es obligatorio');
  });

  it('muestra el mensaje de error del backend', async () => {
    login.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 401, error: { message: 'Email o contrasena incorrectos' },
    })));
    const { fixture, el } = render();
    type(el, '#email', 'a@test.local');
    type(el, '#password', 'mala');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(login).toHaveBeenCalledWith({ email: 'a@test.local', password: 'mala' });
    expect(el.textContent).toContain('Email o contrasena incorrectos');
  });

  it('lleva a los empleados al panel tras entrar', async () => {
    login.mockReturnValue(of({ permissions: ['EVENT_VIEW'] }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el } = render();
    type(el, '#email', 'staff@test.local');
    type(el, '#password', 'secreta');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/admin');
  });
});
