import * as echarts from 'echarts/core';
import { BarChart, HeatmapChart } from 'echarts/charts';
import {
  GridComponent, LegendComponent, TitleComponent, TooltipComponent, VisualMapComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

// Registro único e tree-shaken: só os módulos que as telas usam.
echarts.use([HeatmapChart, BarChart, GridComponent, TooltipComponent, VisualMapComponent, LegendComponent,
  TitleComponent, CanvasRenderer]);

export { echarts };
