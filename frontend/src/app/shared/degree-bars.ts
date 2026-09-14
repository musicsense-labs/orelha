import {
  Component, DestroyRef, ElementRef, afterNextRender, effect, inject, input, viewChild,
} from '@angular/core';
import { echarts } from './echarts';

export interface DegreeSeries {
  name: string;
  values: number[];
}

/** Distribuição de graus como barras agrupadas (uma série por artista ou por unidade). */
@Component({
  selector: 'app-degree-bars',
  template: '<div #host class="chart" [style.height.px]="height()"></div>',
  styles: ':host { display: block; } .chart { width: 100%; }',
})
export class DegreeBars {
  readonly series = input.required<DegreeSeries[]>();
  readonly labels = input.required<string[]>();
  readonly title = input('');
  readonly height = input(260);

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private chart?: echarts.ECharts;

  constructor() {
    afterNextRender(() => {
      this.chart = echarts.init(this.host().nativeElement);
      this.render();
      const onResize = () => this.chart?.resize();
      window.addEventListener('resize', onResize);
      inject(DestroyRef).onDestroy(() => window.removeEventListener('resize', onResize));
    });
    effect(() => {
      this.series();
      this.labels();
      this.title();
      this.render();
    });
    inject(DestroyRef).onDestroy(() => this.chart?.dispose());
  }

  private render(): void {
    if (!this.chart) {
      return;
    }
    this.chart.setOption({
      title: { text: this.title(), left: 'center', textStyle: { fontSize: 14, fontWeight: 'normal' } },
      tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${(100 * v).toFixed(1)}%` },
      legend: { bottom: 0 },
      grid: { left: 50, right: 20, top: 40, bottom: 50 },
      xAxis: { type: 'category', data: this.labels(), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', axisLabel: { formatter: (v: number) => `${Math.round(100 * v)}%` } },
      series: this.series().map((s) => ({ name: s.name, type: 'bar', data: s.values })),
    }, { replaceMerge: ['series'] });
  }
}
