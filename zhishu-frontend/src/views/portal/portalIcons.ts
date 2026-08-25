import { defineComponent, h } from 'vue'

function strokeIcon(paths: string[]) {
  return defineComponent({
    name: 'PortalStrokeIcon',
    setup() {
      return () =>
        h(
          'svg',
          {
            viewBox: '0 0 24 24',
            fill: 'none',
            stroke: 'currentColor',
            'stroke-width': 1.75,
            'aria-hidden': 'true',
          },
          paths.map((d) =>
            h('path', {
              key: d,
              d,
              'stroke-linecap': 'round',
              'stroke-linejoin': 'round',
            }),
          ),
        )
    },
  })
}

/** 门户页专用轻量图标，避免引入 @element-plus/icons-vue */
export const PortalCpuIcon = strokeIcon([
  'M9 3v2M15 3v2M9 19v2M15 19v2M3 9h2M3 15h2M19 9h2M19 15h2',
  'M7 7h10v10H7z',
])

export const PortalGridIcon = strokeIcon([
  'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
])

export const PortalKeyIcon = strokeIcon([
  'M15.5 7.5a3.5 3.5 0 1 0-2.2 6.2L7 20v-3H4v-3l6.3-6.3',
])

export const PortalConnectionIcon = strokeIcon([
  'M8 12h8M12 8v8M6 6l12 12M18 6L6 18',
])

export const PortalSettingIcon = strokeIcon([
  'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z',
])

export const PortalOdometerIcon = strokeIcon([
  'M12 6v6l3.5 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z',
])

export const PortalSearchIcon = strokeIcon([
  'M11 5a6 6 0 1 0 0 12 6 6 0 0 0 0-12z',
  'M21 21l-4.35-4.35',
])

export const PortalShareIcon = strokeIcon([
  'M18 8a3 3 0 1 0-2.8-4H8.8A3 3 0 1 0 6 8',
  'M6 16a3 3 0 1 0 2.8 4h6.4A3 3 0 1 0 18 16',
  'M8.6 9.5l6.8 4M8.6 14.5l6.8-4',
])

/** 工作流：节点 + 连线 */
export const PortalWorkflowIcon = strokeIcon([
  'M5 5h4v4H5zM15 5h4v4h-4zM10 15h4v4h-4z',
  'M9 7h6M7 9v4l5 4M17 9v4l-5 4',
])

/** Open API：盾牌 + 钥匙孔 */
export const PortalOpenApiIcon = strokeIcon([
  'M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6l7-3z',
  'M12 10a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 13v2.5',
])
