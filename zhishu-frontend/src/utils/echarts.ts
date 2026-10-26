import * as echarts from 'echarts/core'
import { GraphChart, type GraphSeriesOption } from 'echarts/charts'
import {
  LegendComponent,
  TooltipComponent,
  type LegendComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

/**
 * ECharts 按需引入约定。
 *
 * 禁止 `import * as echarts from 'echarts'`：全量引入会把整包打进异步 chunk，
 * 知识图谱页曾因此产出 1.1MB（gzip 385KB）的 chunk。
 * 新增图表类型时：在 `use()` 里补充对应 Chart / Component，并扩展下面的 option 类型。
 */
echarts.use([GraphChart, LegendComponent, TooltipComponent, CanvasRenderer])

/** 知识图谱（graph 力导向图）所需的最小 option 类型集合 */
export type GraphChartOption = echarts.ComposeOption<
  GraphSeriesOption | LegendComponentOption | TooltipComponentOption
>

/** 图表实例类型（等价于 echarts.init 的返回值） */
export type EChartsInstance = echarts.ECharts

export { echarts }
