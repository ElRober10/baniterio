import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AvisoPendientes } from './aviso-pendientes';

/**
 * `AvisoPendientes` es presentacional: pinta una campana + número cuando
 * `cuenta > 0`, nada cuando es 0, y `99+` cuando pasa de 99.
 */
@Component({
  imports: [AvisoPendientes],
  template: `<app-aviso-pendientes [cuenta]="n" />`,
})
class Host {
  n = 0;
}

describe('AvisoPendientes', () => {
  function render(n: number): HTMLElement {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.n = n;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('con cuenta 0 no pinta nada', () => {
    const el = render(0);
    expect(el.textContent?.trim()).toBe('');
    expect(el.querySelector('[role="status"]')).toBeNull();
  });

  it('con cuenta 3 pinta "3" y el aria-label', () => {
    const el = render(3);
    const status = el.querySelector('[role="status"]');
    expect(status).not.toBeNull();
    expect(status?.textContent).toContain('3');
    expect(status?.getAttribute('aria-label')).toBe('3 pendientes');
  });

  it('con cuenta 120 pinta "99+"', () => {
    const el = render(120);
    expect(el.querySelector('[role="status"]')?.textContent).toContain('99+');
    expect(el.querySelector('[role="status"]')?.getAttribute('aria-label')).toBe('120 pendientes');
  });
});
