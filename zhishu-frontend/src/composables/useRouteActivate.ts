import { onActivated, onMounted } from 'vue'

/**
 * 在首次进入与被 keep-alive 缓存后再次激活时执行回调。
 * 解决菜单切换时列表页仅 onMounted 拉数导致偶发空白的问题。
 */
export function useRouteActivate(callback: () => void | Promise<void>) {
  const run = () => {
    void callback()
  }

  onMounted(run)
  onActivated(run)
}
