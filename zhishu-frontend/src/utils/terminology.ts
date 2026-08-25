import type { MenuVO } from '@/types/menu'

/** 将界面展示文案中的「终端」统一替换为「遥测站」 */
export function formatTerminology(text: string): string {
  return text.replace(/终端/g, '遥测站')
}

/** 递归处理菜单树标题 */
export function applyTerminologyToMenus(menus: MenuVO[]): MenuVO[] {
  return menus.map((menu) => ({
    ...menu,
    title: formatTerminology(menu.title),
    children: menu.children ? applyTerminologyToMenus(menu.children) : undefined,
  }))
}
