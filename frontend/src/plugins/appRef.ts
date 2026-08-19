import type { App } from 'vue'

let appInstance: App | null = null

export function setAppInstance(app: App) {
  appInstance = app
}

export function getAppInstance() {
  return appInstance
}
