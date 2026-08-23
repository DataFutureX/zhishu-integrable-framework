import { isDemoMode } from '@/config/demo'
import type { ProjectEntity } from '@/types/project'

const DEMO_PROJECTS: ProjectEntity[] = [
  { id: 1, projectName: '青溪防洪工程', code: 'QX-FH' },
  { id: 2, projectName: '绿野湖补水工程', code: 'LY-BS' },
  { id: 3, projectName: '东区雨洪工程', code: 'DQ-YH' },
]

/** 图谱页工程筛选。知枢无独立工程台账，联调态返回空列表（可查看全部工程）。 */
export function getProjectListApi(): Promise<ProjectEntity[]> {
  if (isDemoMode) return Promise.resolve(DEMO_PROJECTS)
  return Promise.resolve([])
}
