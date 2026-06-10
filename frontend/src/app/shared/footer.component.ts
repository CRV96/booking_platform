import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: `
    <footer class="ftr">
      <span>© 2026 CRV Bookings</span>
      <span>Amsterdam · Berlin · Lisbon · London · Paris · Porto</span>
    </footer>
  `,
})
export class FooterComponent {}
