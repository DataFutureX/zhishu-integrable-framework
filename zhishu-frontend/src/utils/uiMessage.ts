type ElMessageApi = typeof import('element-plus')['ElMessage']

let messageApiPromise: Promise<ElMessageApi> | null = null

function loadMessageApi(): Promise<ElMessageApi> {
  messageApiPromise ??= import('element-plus').then(({ ElMessage }) => ElMessage)
  return messageApiPromise
}

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
