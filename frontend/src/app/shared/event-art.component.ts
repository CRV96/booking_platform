import { Component, Input, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

const PALETTES = [
  { bg: '#1e1c17', fg: '#f2e7c9', acc: '#b8864a' },
  { bg: '#2a2a2a', fg: '#e9e4d6', acc: '#9aa381' },
  { bg: '#efece1', fg: '#1c1c1c', acc: '#b64b2d' },
  { bg: '#0f2129', fg: '#cfe4e2', acc: '#d9a35a' },
  { bg: '#f3d9b1', fg: '#2a1a0e', acc: '#7a3b12' },
  { bg: '#1a1a24', fg: '#d8d5ff', acc: '#c277ff' },
  { bg: '#e7e1d3', fg: '#2a2621', acc: '#2b5d4a' },
  { bg: '#2b1a18', fg: '#f7e3d4', acc: '#e28160' },
];

function rings(p: typeof PALETTES[0], seed: number, _title?: string): string {
  const cx = 80 + (seed * 17) % 60;
  let circles = '';
  for (let i = 0; i < 14; i++) {
    const r = 14 + i * 12;
    const stroke = i === 6 ? p.acc : p.fg;
    const opacity = i === 6 ? 1 : Math.max(0, 0.35 - i * 0.02);
    circles += `<circle cx="${cx}" cy="100" r="${r}" fill="none" stroke="${stroke}" stroke-opacity="${opacity}" stroke-width="1"/>`;
  }
  circles += `<circle cx="${cx}" cy="100" r="4" fill="${p.acc}"/>`;
  return `<rect width="320" height="200" fill="${p.bg}"/>${circles}`;
}

function stripes(p: typeof PALETTES[0], seed: number, _title?: string): string {
  let rects = `<rect width="320" height="200" fill="${p.bg}"/>`;
  for (let i = 0; i < 16; i++) {
    const w = 10 + ((seed + i) % 5);
    const fill = i === (seed % 16) ? p.acc : p.fg;
    const opacity = i === (seed % 16) ? 1 : 0.08 + (i % 3) * 0.04;
    rects += `<rect x="${i * 20}" y="0" width="${w}" height="200" fill="${fill}" opacity="${opacity}"/>`;
  }
  return rects;
}

function grid(p: typeof PALETTES[0], seed: number, _title?: string): string {
  let out = `<rect width="320" height="200" fill="${p.bg}"/>`;
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 12; x++) {
      const hit = ((x * 3 + y * 7 + seed) % 11) < 2;
      out += `<rect x="${20 + x * 24}" y="${14 + y * 22}" width="16" height="14" fill="${hit ? p.acc : p.fg}" opacity="${hit ? 1 : 0.12}"/>`;
    }
  }
  return out;
}

function arc(p: typeof PALETTES[0], seed: number, _title?: string): string {
  const y1 = 200 - (seed % 60), y2 = 20 + (seed * 5) % 80, y3 = 180 - (seed % 50);
  const y4 = 210 - (seed % 60), y5 = 30 + (seed * 5) % 80, y6 = 190 - (seed % 50);
  return `<rect width="320" height="200" fill="${p.bg}"/>
    <path d="M 0 ${y1} Q 160 ${y2}, 320 ${y3}" fill="none" stroke="${p.fg}" stroke-opacity="0.35" stroke-width="1"/>
    <path d="M 0 ${y4} Q 160 ${y5}, 320 ${y6}" fill="none" stroke="${p.acc}" stroke-width="2"/>
    <circle cx="160" cy="110" r="${60 + (seed % 20)}" fill="none" stroke="${p.fg}" stroke-opacity="0.25"/>`;
}

function typeArt(p: typeof PALETTES[0], seed: number, title?: string): string {
  const letter = ((title || 'E')[0] || 'E').toUpperCase();
  const w = 60 + (seed * 11) % 200;
  return `<rect width="320" height="200" fill="${p.bg}"/>
    <text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle"
      font-family="Instrument Serif, serif" font-style="italic" font-size="220" fill="${p.fg}" opacity="0.95">${letter}</text>
    <rect x="0" y="180" width="${w}" height="4" fill="${p.acc}"/>`;
}

function noise(p: typeof PALETTES[0], seed: number, _title?: string): string {
  let out = `<rect width="320" height="200" fill="${p.bg}"/>`;
  for (let i = 0; i < 70; i++) {
    const x = (i * 37 + seed * 13) % 320;
    const y = (i * 61 + seed * 7) % 200;
    const r = 1 + ((i * 7 + seed) % 4);
    const isAcc = (i + seed) % 9 === 0;
    out += `<circle cx="${x}" cy="${y}" r="${r}" fill="${isAcc ? p.acc : p.fg}" opacity="${isAcc ? 1 : 0.35}"/>`;
  }
  return out;
}

type Renderer = (p: typeof PALETTES[0], seed: number, title?: string) => string;
const RENDERERS: Renderer[] = [rings, stripes, grid, arc, typeArt, noise];

@Component({
  selector: 'app-event-art',
  standalone: true,
  template: `<div class="art" [style.aspectRatio]="ratio" [innerHTML]="svg()"></div>`,
  styles: [`:host { display: block; } .art { position: relative; overflow: hidden; border-radius: 4px; background: #f2f1ec; } .art svg { display: block; width: 100%; height: 100%; }`],
})
export class EventArtComponent {
  @Input() seed = 1;
  @Input() title = '';
  @Input() ratio = '16/10';
  private sanitizer = inject(DomSanitizer);

  svg(): SafeHtml {
    const p = PALETTES[this.seed % PALETTES.length];
    const idx = this.seed % RENDERERS.length;
    const inner = RENDERERS[idx](p, this.seed, this.title);
    const svgStr = `<svg viewBox="0 0 320 200" preserveAspectRatio="xMidYMid slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`;
    return this.sanitizer.bypassSecurityTrustHtml(svgStr);
  }
}
