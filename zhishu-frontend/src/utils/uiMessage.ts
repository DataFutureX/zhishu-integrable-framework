type ElMessageApi = (typeof import('element-plus'))['ElMessage']

let messageApiPromise: Promise<ElMessageApi> | null = null

function loadMessageApi(): Promise<ElMessageApi> {
  messageApiPromise ??= import('element-plus').then(({ ElMessage }) => ElMessage)
  return messageApiPromise
}

/**
 * 全局错误提示的唯一出口（由 request 拦截器调用）。
 * 业务层 catch 中不要再弹 `ElMessage.error`，否则同一失败会弹两次；
 * 需要自定义文案的请求请改为传 `skipErrorMessage: true` 后自行提示。
 */
export function showErrorMessage(message: string) {
  void loadMessageApi().then((ElMessage) => {
    ElMessage.error(message)
  })
}

export function showSuccessMessage(message: string) {
  void loadMessageApi().then((ElMessage) => {
    ElMessage.success(message)
  })
}

/** 是否为 ElMessageBox 的取消 / 关闭（属于预期操作，不应作为错误上报） */
export function isMessageBoxCancel(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}
