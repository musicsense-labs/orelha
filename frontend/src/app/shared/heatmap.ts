import {
  Component, DestroyRef, ElementRef, afterNextRender, effect, inject, input, viewChild,
} from '@angular/core';
import { echarts } from './echarts';

/** Matriz de transição grau → grau como heatmap (linhas = de, colunas = para). */
@Component({
  selector: 'app-heatmap',
  template: '<div #host class="chart" [style.height.px]="height()"></div>',
  styles: ':host { display: block; } .chart { width: 100%; }',
})
export class Heatmap {
  readonly matrix = input.required<number[][]>();
  readonly counts = input<number[][] | null>(null);
  readonly labels = input.required<string[]>();
  readonly title = input('');
  readonly height = input(420);

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
      this.matrix();
      this.labels();
      this.title();
      this.counts();
      this.render();
    });
    inject(DestroyRef).onDestroy(() => this.chart?.dispose());
  }

  private render(): void {
    if (!this.chart) {
      return;
    }
    const labels = this.labels();
    const matrix = this.matrix();
    const counts = this.counts();
    const data: [number, number, number][] = [];
    matrix.forEach((row, from) => row.forEach((p, to) => data.push([to, from, p])));
    this.chart.setOption({
      title: { text: this.title(), left: 'center', textStyle: { fontSize: 14, fontWeight: 'normal' } },
      tooltip: {
        formatter: (params: { value: [number, number, number] }) => {
          const [to, from, p] = params.value;
          const n = counts ? ` · ${counts[from][to]}×` : '';
          return `${labels[from]} → ${labels[to]}: ${(100 * p).toFixed(0)}%${n}`;
        },
      },
      grid: { left: 60, right: 20, top: 40, bottom: 60 },
      xAxis: { type: 'category', data: labels, name: 'para', nameLocation: 'middle', nameGap: 28,
        axisLabel: { fontSize: 11 }, splitArea: { show: true } },
      yAxis: { type: 'category', data: labels, name: 'de', inverse: true, axisLabel: { fontSize: 11 },
        splitArea: { show: true } },
      visualMap: { min: 0, max: 1, show: false, inRange: { color: ['#f6f1e8', '#e9c06a', '#d9532b', '#1c1a17'] } },
      series: [{ type: 'heatmap', data, emphasis: { itemStyle: { borderColor: '#1c1a17', borderWidth: 1 } } }],
    });
  }
}
