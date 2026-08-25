/**
 * AI 助手演示 Mock —— 与 mock/data.ts 演示站台账对齐
 * 遥测站 6（在线 4 / 离线 2）、视频站 3、工程 3、告警 3
 */
import type { AiSseHandlers } from '@/utils/aiSse'
import type {
  AlarmSummary,
  ChatRequestDTO,
  ChatResponseVO,
  ChatStructuredRequestDTO,
  StationCompareResult,
  TrendAnalysisResult,
} from '@/types/aiChat'
import type { DocumentQueryDTO, DocumentUploadParams, DocumentVO } from '@/types/aiDocument'
import type { QaHistoryScene, QaHistoryVO } from '@/types/qaHistory'
import { daysAgoStr, delay, nowStr } from './utils'

/** 与 mock/data.ts 中 mockTerminals / mockAlerts 保持一致 */
const DEMO = {
  stations: {
    qx: { code: 'DEMO0001001', name: '青溪水文站', online: true },
    bh: { code: 'DEMO0001002', name: '碧湖监测站', online: true },
    rain: { code: 'DEMO0001003', name: '东区雨量站', online: false },
    level: { code: 'DEMO0001004', name: '西区液位站', online: true },
    tide: { code: 'DEMO0001005', name: '南岸潮位站', online: true },
    flow: { code: 'DEMO0001006', name: '北港流量站', online: false },
  },
  onlineCount: 4,
  offlineCount: 2,
  totalCount: 6,
  videoCount: 3,
  projectCount: 3,
  qxWaterLevel: 12.35,
  qxFlow: 86.2,
  rainDaily: 62.5,
  rainThreshold: 50,
  levelAlert: 16.2,
  levelThreshold: 15,
} as const

function buildChatReply(message: string, enableRag?: boolean): string {
  const q = message.trim()
  const ragHint = enableRag
    ? '\n\n> 已启用知识库增强：结合《水文监测数据通信规约》《水位监测操作手册》等资料作答。'
    : ''

  if (/告警|报警|阈值|预警/.test(q)) {
    return (
      `【演示模式】当前未恢复告警 **2** 条、已恢复 **1** 条：\n\n` +
      `| 站点 | 要素 | 级别 | 实测 | 阈值 | 状态 |\n` +
      `| --- | --- | --- | --- | --- | --- |\n` +
      `| ${DEMO.stations.rain.name}（${DEMO.stations.rain.code}） | 雨量 | 二级 | ${DEMO.rainDaily} mm | ≥${DEMO.rainThreshold} mm | 未恢复 |\n` +
      `| ${DEMO.stations.level.name}（${DEMO.stations.level.code}） | 水位 | 一级 | ${DEMO.levelAlert} m | ≥${DEMO.levelThreshold} m | 未恢复 |\n` +
      `| ${DEMO.stations.flow.name}（${DEMO.stations.flow.code}） | 电压 | 三级 | 10.2 V | ≤11 V | 已恢复 |\n\n` +
      `建议优先复核东区雨量与西区液位，并关注北港流量站供电情况。` +
      ragHint
    )
  }

  if (/雨量|降雨|降水/.test(q)) {
    return (
      `【演示模式】雨情摘要：\n` +
      `- **${DEMO.stations.rain.name}**（${DEMO.stations.rain.code}）：今日累计 **${DEMO.rainDaily} mm**，已超过二级阈值 ${DEMO.rainThreshold} mm，站点当前**离线**（末次上报约 2 天前）。\n` +
      `- 青溪防洪工程辖区另有青溪水文站可作水位/流量交叉校核。\n` +
      `- 演示环境雨量要素编码为 \`P\` / \`pn05\`。` +
      ragHint
    )
  }

  if (/水位|液位|潮位|水情/.test(q)) {
    return (
      `【演示模式】水情摘要：\n` +
      `- **${DEMO.stations.qx.name}**：水位 **${DEMO.qxWaterLevel} m**，流量 **${DEMO.qxFlow} m³/s**，在线正常。\n` +
      `- **${DEMO.stations.level.name}**：实测水位 **${DEMO.levelAlert} m**，超过一级阈值 ${DEMO.levelThreshold} m，请关注绿野湖补水调度。\n` +
      `- **${DEMO.stations.tide.name}**：南岸潮位站在线，可用于感潮河段对照。\n` +
      `- 演示环境水位要素编码为 \`Z\`。` +
      ragHint
    )
  }

  if (/流量|流速/.test(q)) {
    return (
      `【演示模式】流量相关站点：\n` +
      `- **${DEMO.stations.qx.name}**：瞬时流量约 **${DEMO.qxFlow} m³/s**（在线）。\n` +
      `- **${DEMO.stations.flow.name}**（${DEMO.stations.flow.code}）：当前**离线**，末次上报约 1 天前，并曾出现电压偏低三级告警。\n` +
      `- 协议：青溪站为 SL 651-2014，北港站为 SL/T 427-2021。` +
      ragHint
    )
  }

  if (/在线|离线|站点|遥测|终端|概况|总览|多少站/.test(q)) {
    return (
      `【演示模式】平台监测概况（与演示台账一致）：\n` +
      `- 遥测站 **${DEMO.totalCount}** 个：在线 **${DEMO.onlineCount}**、离线 **${DEMO.offlineCount}**\n` +
      `- 视频站 **${DEMO.videoCount}** 个（青溪大坝 / 碧湖水库 / 绿野湖补水口）\n` +
      `- 工程 **${DEMO.projectCount}** 个：青溪防洪、碧湖水库调度、绿野湖生态补水\n` +
      `- 协议接入：SL 651-2014 与 SL/T 427-2021 双通道\n\n` +
      `| 站名 | 站号 | 状态 | 工程 |\n` +
      `| --- | --- | --- | --- |\n` +
      `| ${DEMO.stations.qx.name} | ${DEMO.stations.qx.code} | 在线 | 青溪防洪工程 |\n` +
      `| ${DEMO.stations.bh.name} | ${DEMO.stations.bh.code} | 在线 | 碧湖水库调度工程 |\n` +
      `| ${DEMO.stations.rain.name} | ${DEMO.stations.rain.code} | 离线 | 青溪防洪工程 |\n` +
      `| ${DEMO.stations.level.name} | ${DEMO.stations.level.code} | 在线 | 绿野湖生态补水工程 |\n` +
      `| ${DEMO.stations.tide.name} | ${DEMO.stations.tide.code} | 在线 | 绿野湖生态补水工程 |\n` +
      `| ${DEMO.stations.flow.name} | ${DEMO.stations.flow.code} | 离线 | 碧湖水库调度工程 |` +
      ragHint
    )
  }

  if (/青溪/.test(q)) {
    return (
      `【演示模式】**青溪水文站**（${DEMO.stations.qx.code}）属青溪防洪工程：\n` +
      `- 安装位置：东区青溪大桥\n` +
      `- 最新水位 ${DEMO.qxWaterLevel} m，流量 ${DEMO.qxFlow} m³/s\n` +
      `- 协议 SL 651-2014，当前在线\n` +
      `- 关联视频：青溪大坝监控（VID-QX-01）` +
      ragHint
    )
  }

  if (/碧湖/.test(q)) {
    return (
      `【演示模式】**碧湖监测站**（${DEMO.stations.bh.code}）属碧湖水库调度工程：\n` +
      `- 安装位置：西区碧湖入库口，当前在线\n` +
      `- 同工程还有离线的北港流量站，建议一并巡检\n` +
      `- 关联视频：碧湖水库监控（VID-BH-01）` +
      ragHint
    )
  }

  if (/巡检|计划|任务|打卡|检查点|异常闭环/.test(q)) {
    return (
      `【演示模式】巡检业务摘要（只读 Tool）：\n\n` +
      `| 类型 | 内容 |\n` +
      `| --- | --- |\n` +
      `| 启用计划 | 青溪防洪周巡、碧湖入库月检 |\n` +
      `| 进行中 | **青溪防洪周巡-本周**（检查点 1/2） |\n` +
      `| 待开始 | 碧湖入库月检-本月（已派发） |\n` +
      `| 未关闭异常 | 东区雨量站离线待复核（二级） |\n\n` +
      `建议优先完成东区雨量站打卡并关闭离线异常；写操作请在「巡检管理」执行。` +
      ragHint
    )
  }

  if (/图谱|拓扑|邻居|影响面|关联路径|知识图谱|GraphRAG/.test(q)) {
    return (
      `【演示模式】业务拓扑知识图谱（模拟 Neo4j / GraphRAG）：\n\n` +
      `| 实体 | 示例 |\n` +
      `| --- | --- |\n` +
      `| 工程 | 青溪防洪 / 碧湖水库 / 绿野湖补水 |\n` +
      `| 终端 | 青溪水文站 → 含东区雨量站等 |\n` +
      `| 告警影响 | 雨量二级告警 → 东区雨量站 → 青溪防洪工程 |\n` +
      `| 巡检链路 | 周巡计划 → 本周任务 → 离线异常 → 东区雨量站 |\n\n` +
      `可在「智能中心 / 知识图谱」查看子图、搜索实体、分析路径与告警影响面；也可切换 **知识图谱智能体（kg_agent）** 对话研判。` +
      ragHint
    )
  }

  if (/NL2SQL|自然语言查数|执行.?SQL|白名单.?SQL|查表|统计一下/.test(q)) {
    return (
      `【演示模式】NL2SQL 查数示意（白名单只读，不真实执行）：\n\n` +
      '```sql\n' +
      'SELECT terminal_name, actual_value, threshold_value, status\n' +
      'FROM t_terminal_alert\n' +
      "WHERE status = 0\n" +
      'ORDER BY create_time DESC\n' +
      'LIMIT 20;\n' +
      '```\n\n' +
      `模拟结果：未恢复告警 2 条（东区雨量、西区液位）。可切换 **数据分析智能体（nl2sql_agent）** 体验该能力。` +
      ragHint
    )
  }

  if (/协议|报文|SL\s*651|427|通信规约/.test(q)) {
    return (
      `【演示模式】协议接入说明：\n` +
      `- **SL 651-2014**：青溪水文站、碧湖监测站、西区液位站、南岸潮位站\n` +
      `- **SL/T 427-2021**：东区雨量站、北港流量站\n` +
      `- 知识库中可查阅《水文监测数据通信规约》《SL 651 与 SL/T 427 接入说明》` +
      ragHint
    )
  }

  if (/能力|能做什么|帮助|功能|你好|您好|hi|hello/i.test(q)) {
    return (
      `您好，我是「数智未来 / 万象监测」演示 AI 助手。可协助：\n\n` +
      `1. **水雨情问答**：水位、流量、雨量、站况（对齐演示台账）\n` +
      `2. **告警摘要**：当前雨量/水位/电压告警解读\n` +
      `3. **巡检只读**：计划 / 任务进度 / 未关闭异常\n` +
      `4. **知识图谱**：工程—终端—告警—巡检拓扑（GraphRAG）\n` +
      `5. **NL2SQL**：白名单只读 SQL 查数示意\n` +
      `6. **结构化输出**：对比表格、趋势分析、告警摘要模式\n` +
      `7. **知识库增强**：基于规约与操作手册的 RAG 演示\n\n` +
      `试试：「青溪站水位怎么样」「告警影响面」「本周巡检进度如何」「自然语言查数」` +
      ragHint
    )
  }

  return (
    `【演示模式】您询问了「${q.slice(0, 40)}${q.length > 40 ? '…' : ''}」。\n\n` +
    `演示台账摘要：遥测站 ${DEMO.totalCount} 个（在线 ${DEMO.onlineCount} / 离线 ${DEMO.offlineCount}），` +
    `视频站 ${DEMO.videoCount} 个，工程 ${DEMO.projectCount} 个。` +
    `青溪水文站水位 ${DEMO.qxWaterLevel} m、流量 ${DEMO.qxFlow} m³/s；` +
    `东区雨量站今日累计 ${DEMO.rainDaily} mm（二级告警）。\n\n` +
    `可继续追问站点、告警、雨量或水位；或切换「对比表格 / 趋势分析 / 告警摘要」模式。` +
    ragHint
  )
}

function buildDocumentQaReply(question: string, documentId?: string, topK = 5): string {
  const q = question.trim()
  const doc = documentId ? demoDocuments.find((d) => d.id === documentId) : undefined
  const scope = doc ? `《${doc.fileName}》` : '全部已上传文档'

  if (/水位|液位|警戒|调度/.test(q)) {
    return (
      `【演示模式 · 知识问答】基于「${scope}」检索（topK=${topK}）：\n\n` +
      `根据《水位监测操作手册》：\n` +
      `1. 水位要素编码为 \`Z\`，单位 m，宜与闸门开度、流量联合研判；\n` +
      `2. 一级警戒示例阈值 **${DEMO.levelThreshold} m**（演示值）；西区液位站当前 **${DEMO.levelAlert} m** 已超阈值；\n` +
      `3. 超阈值时应核对青溪水文站水位/流量，并通知调度值班。\n\n` +
      `> 演示环境为模拟检索结果，关闭演示模式后可对接真实向量库。`
    )
  }

  if (/协议|报文|帧|651|427|规约/.test(q)) {
    return (
      `【演示模式 · 知识问答】基于「${scope}」检索（topK=${topK}）：\n\n` +
      `根据《水文监测数据通信规约》与《SL 651 / SL/T 427 接入说明》：\n` +
      `- 报文一般包含帧头、功能码、数据域、校验码；\n` +
      `- SL 651-2014 与 SL/T 427-2021 双通道并行，站号互不冲突；\n` +
      `- 演示站中雨量站、北港流量站走 427，其余走 651。\n\n` +
      `> 演示环境为模拟检索结果，关闭演示模式后可对接真实向量库。`
    )
  }

  if (/告警|阈值|雨量/.test(q)) {
    return (
      `【演示模式 · 知识问答】基于「${scope}」检索（topK=${topK}）：\n\n` +
      `手册建议：雨量要素 \`P\` 二级阈值示例 **${DEMO.rainThreshold} mm**。` +
      `东区雨量站演示值为 **${DEMO.rainDaily} mm**，应按汛期加强监测流程复核上报链路与供电状态。\n\n` +
      `> 演示环境为模拟检索结果。`
    )
  }

  return (
    `【演示模式 · 知识问答】基于「${scope}」检索（topK=${topK}）：\n\n` +
    `关于「${q}」：\n` +
    `- 已在知识库中匹配到规约/手册相关片段（演示）；\n` +
    `- 平台演示数据含 ${DEMO.totalCount} 个遥测站与 ${DEMO.videoCount} 个视频站；\n` +
    `- 可尝试提问「水位警戒如何处置」「SL651 报文结构」「雨量阈值」。\n\n` +
    `> 关闭演示模式后可对接 AI 服务真实 RAG 接口。`
  )
}

let nextDocId = 100
let nextHistoryId = 10

const demoDocuments: DocumentVO[] = [
  {
    id: '1',
    fileName: '水文监测数据通信规约.pdf',
    fileType: 'pdf',
    fileSize: 3812099,
    uploadTime: daysAgoStr(12),
    processed: true,
    content:
      '水文监测数据通信规约（演示）\n\n' +
      '1 范围\n本标准规定了水文监测系统数据传输的协议格式、帧结构与校验方式。\n\n' +
      '2 术语\n遥测站：具备自动采集并上报水文要素能力的现场设备。\n\n' +
      '3 报文结构\n帧头、功能码、数据域、校验码。\n\n' +
      '4 协议通道\n支持 SL 651-2014 与 SL/T 427-2021，演示环境双通道并行。',
  },
  {
    id: '2',
    fileName: '水位监测操作手册.md',
    fileType: 'md',
    fileSize: 16384,
    uploadTime: daysAgoStr(8),
    processed: true,
    content:
      '水位监测操作手册（演示）\n\n' +
      '## 要素说明\n- 水位要素编码 Z，单位 m\n- 宜与流量 Q、雨量 P 联合分析\n\n' +
      '## 警戒与处置\n- 一级警戒示例阈值 15 m\n- 超阈值：核对青溪水文站，通知调度值班，查看西区液位站\n\n' +
      '## 演示站点\n青溪水文站 DEMO0001001、西区液位站 DEMO0001004、南岸潮位站 DEMO0001005',
  },
  {
    id: '3',
    fileName: 'SL651与SL427接入说明.docx',
    fileType: 'docx',
    fileSize: 24576,
    uploadTime: daysAgoStr(5),
    processed: true,
    content:
      'SL 651-2014 / SL/T 427-2021 接入说明（演示）\n\n' +
      '- 651 默认端口 9000，427 默认端口 9001\n' +
      '- 站号分段：651 自 1 起，427 自 10001 起，避免冲突\n' +
      '- 演示站：雨量站、北港流量站走 427；其余走 651',
  },
  {
    id: '4',
    fileName: '汛期值班应急预案.pdf',
    fileType: 'pdf',
    fileSize: 102400,
    uploadTime: daysAgoStr(1),
    processed: false,
    content: '',
  },
]

const demoHistory: QaHistoryVO[] = [
  {
    id: '1',
    scene: 'CHAT',
    question: '现在平台有多少遥测站在线？',
    answer: buildChatReply('现在平台有多少遥测站在线？'),
    model: 'demo-mock',
    documentId: undefined,
    conversationId: 'demo-chat-seed',
    createTime: daysAgoStr(0, 9, 12),
  },
  {
    id: '2',
    scene: 'CHAT',
    question: '青溪站水位怎么样？',
    answer: buildChatReply('青溪站水位怎么样？'),
    model: 'demo-mock',
    documentId: undefined,
    conversationId: 'demo-chat-seed',
    createTime: daysAgoStr(0, 9, 15),
  },
  {
    id: '3',
    scene: 'CHAT',
    question: '当前有哪些告警？',
    answer: buildChatReply('当前有哪些告警？'),
    model: 'demo-mock',
    documentId: undefined,
    conversationId: 'demo-chat-seed',
    createTime: daysAgoStr(0, 9, 20),
  },
  {
    id: '3b',
    scene: 'CHAT',
    question: '本周巡检进度如何？',
    answer: buildChatReply('本周巡检进度如何？'),
    model: 'demo-mock',
    documentId: undefined,
    conversationId: 'demo-inspection-seed',
    createTime: daysAgoStr(0, 9, 25),
  },
  {
    id: '4',
    scene: 'DOCUMENT_QA',
    question: '水位超过警戒线怎么处理？',
    answer: buildDocumentQaReply('水位超过警戒线怎么处理？', '2'),
    model: 'demo-rag-mock',
    documentId: '2',
    conversationId: 'demo-doc-seed',
    createTime: daysAgoStr(0, 8, 40),
  },
]

function ensureConversationId(id?: string) {
  return id?.trim() || `demo-${crypto.randomUUID?.() || Date.now()}`
}

function pushHistory(
  scene: QaHistoryScene,
  question: string,
  answer: string,
  model: string,
  documentId?: string,
  conversationId?: string,
) {
  demoHistory.push({
    id: String(nextHistoryId++),
    scene,
    question,
    answer,
    model,
    documentId: documentId,
    conversationId: conversationId,
    createTime: nowStr(),
  })
}

function buildDemoProgressEvents(message: string): Array<{
  event: {
    type: string
    name: string
    detail?: string | null
    durationMs?: number | null
    timestamp?: number | null
  }
  delayMs: number
}> {
  const q = message.trim()
  const ts = () => Date.now()
  if (/巡检|计划|任务|打卡|检查点|异常闭环/.test(q)) {
    return [
      {
        event: { type: 'NODE_START', name: 'SEQUENTIAL · 澄清', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 320,
      },
      {
        event: {
          type: 'NODE_END',
          name: 'SEQUENTIAL · 澄清',
          detail: '汇总本周巡检进度与未关闭异常',
          durationMs: 168,
          timestamp: ts(),
        },
        delayMs: 420,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: 'getInspectionSummary',
          detail: '{"range":"week"}',
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 480,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: 'getInspectionSummary',
          detail: '{"inProgress":1,"pending":1,"openIssues":1}',
          durationMs: 224,
          timestamp: ts(),
        },
        delayMs: 520,
      },
      {
        event: { type: 'NODE_START', name: 'LLM · 润色', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 280,
      },
    ]
  }
  if (/图谱|拓扑|邻居|影响面|关联路径|知识图谱|GraphRAG/.test(q)) {
    return [
      {
        event: { type: 'NODE_START', name: 'GraphRAG · 实体提示', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 280,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: 'searchGraphEntities',
          detail: '{"keyword":"东区雨量"}',
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 420,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: 'searchGraphEntities',
          detail: '{"found":true,"count":2}',
          durationMs: 160,
          timestamp: ts(),
        },
        delayMs: 400,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: 'getAlertImpact',
          detail: '{"bizId":8001,"depth":2}',
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 460,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: 'getAlertImpact',
          detail: '{"found":true,"nodes":5}',
          durationMs: 210,
          timestamp: ts(),
        },
        delayMs: 480,
      },
      {
        event: { type: 'NODE_START', name: 'LLM · 生成回答', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 260,
      },
    ]
  }
  if (/NL2SQL|自然语言查数|执行.?SQL|白名单.?SQL/.test(q)) {
    return [
      {
        event: { type: 'NODE_START', name: 'NL2SQL · 理解意图', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 300,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: 'describeBizSchema',
          detail: '{"tables":["t_terminal_alert"]}',
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 400,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: 'describeBizSchema',
          detail: 'ok',
          durationMs: 90,
          timestamp: ts(),
        },
        delayMs: 360,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: 'executeReadonlySql',
          detail: '{"limit":20}',
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 440,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: 'executeReadonlySql',
          detail: '{"rows":2}',
          durationMs: 150,
          timestamp: ts(),
        },
        delayMs: 420,
      },
      {
        event: { type: 'NODE_START', name: 'LLM · 生成回答', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 260,
      },
    ]
  }
  if (/告警|报警|阈值|预警|水位|雨量|流量|站点|遥测|青溪|碧湖|在线|离线/.test(q)) {
    return [
      {
        event: { type: 'NODE_START', name: 'REACT · 理解意图', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 300,
      },
      {
        event: {
          type: 'NODE_END',
          name: 'REACT · 理解意图',
          detail: q.slice(0, 48) || '查询监测数据并研判',
          durationMs: 186,
          timestamp: ts(),
        },
        delayMs: 400,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: 'getLatestElement',
          detail: '{"station":"DEMO0001001","element":"水位"}',
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 460,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: 'getLatestElement',
          detail: `{"value":${DEMO.qxWaterLevel},"unit":"m"}`,
          durationMs: 142,
          timestamp: ts(),
        },
        delayMs: 420,
      },
      {
        event: {
          type: 'TOOL_CALL',
          name: /告警|报警|阈值|预警|超限/.test(q) ? 'queryAlerts' : 'getOnlineStatus',
          detail: null,
          durationMs: null,
          timestamp: ts(),
        },
        delayMs: 400,
      },
      {
        event: {
          type: 'TOOL_RESULT',
          name: /告警|报警|阈值|预警|超限/.test(q) ? 'queryAlerts' : 'getOnlineStatus',
          detail: 'ok',
          durationMs: 118,
          timestamp: ts(),
        },
        delayMs: 380,
      },
      {
        event: { type: 'NODE_START', name: 'LLM · 生成回答', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 260,
      },
    ]
  }
  if (/协议|报文|规约|知识|手册|怎么|如何/.test(q)) {
    return [
      {
        event: { type: 'NODE_START', name: 'Hybrid RAG', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 360,
      },
      {
        event: {
          type: 'NODE_END',
          name: 'Hybrid RAG',
          detail: '已注入【知识库检索片段】',
          durationMs: 280,
          timestamp: ts(),
        },
        delayMs: 480,
      },
      {
        event: { type: 'NODE_START', name: 'LLM · 生成回答', detail: null, durationMs: null, timestamp: ts() },
        delayMs: 260,
      },
    ]
  }
  return [
    {
      event: { type: 'NODE_START', name: 'REACT · 理解意图', detail: null, durationMs: null, timestamp: ts() },
      delayMs: 300,
    },
    {
      event: {
        type: 'NODE_END',
        name: 'REACT · 理解意图',
        detail: q.slice(0, 48) || '通用问答',
        durationMs: 140,
        timestamp: ts(),
      },
      delayMs: 360,
    },
    {
      event: { type: 'NODE_START', name: 'LLM · 生成回答', detail: null, durationMs: null, timestamp: ts() },
      delayMs: 260,
    },
  ]
}

/** 中文按字、英文按词拆分，模拟真流式 token */
function tokenizeDemoStream(content: string): string[] {
  const tokens: string[] = []
  for (const part of content.split(/(\n+)/)) {
    if (!part) continue
    if (/^\n+$/.test(part)) {
      tokens.push(part)
      continue
    }
    const matches = part.match(/[\u4e00-\u9fff]|[a-zA-Z0-9._%-]+|\s+|[^\s\u4e00-\u9fff]/g)
    if (matches) tokens.push(...matches)
  }
  return tokens.length ? tokens : [content]
}

export async function mockAiChat(data: ChatRequestDTO): Promise<ChatResponseVO> {
  await delay(600 + Math.random() * 400)
  const conversationId = ensureConversationId(data.conversationId)
  const content = buildChatReply(data.message, data.enableRag)
  pushHistory('CHAT', data.message, content, 'demo-mock', undefined, conversationId)
  return {
    content,
    timestamp: nowStr(),
    model: 'demo-mock',
    conversationId,
  }
}

/**
 * 演示模式 Agent 会话流式：对齐真实 SSE
 * progress（节点/Tool）→ message（真流式增量）→ trace → done
 */
export async function mockChatStream(data: ChatRequestDTO, handlers: AiSseHandlers): Promise<void> {
  const conversationId = ensureConversationId(data.conversationId)
  const content = buildChatReply(data.message, data.enableRag)
  const traces: Array<{
    type: string
    name: string
    detail?: string | null
    durationMs?: number | null
    timestamp?: number | null
  }> = []

  await delay(180)

  const steps = buildDemoProgressEvents(data.message)
  for (const step of steps) {
    const event = { ...step.event, timestamp: Date.now() }
    traces.push(event)
    handlers.onProgress?.(JSON.stringify(event))
    await delay(step.delayMs)
  }

  const tokens = tokenizeDemoStream(content)
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]
    let wait = 16
    if (/[\u4e00-\u9fff]/.test(token)) wait = 24
    else if (/^[a-zA-Z0-9]/.test(token)) wait = 10
    if (token.includes('\n')) wait += 40
    if (/[，。！？、；：]/.test(token)) wait += 36
    await delay(wait)
    handlers.onMessage?.(token)
  }

  handlers.onTrace?.(JSON.stringify(traces))
  pushHistory('CHAT', data.message, content, 'demo-mock', undefined, conversationId)
  await delay(80)
  handlers.onDone?.(conversationId)
}

export async function mockChatStructured(data: ChatStructuredRequestDTO): Promise<ChatResponseVO> {
  await delay(500)
  const conversationId = ensureConversationId(data.conversationId)
  let structured: StationCompareResult | TrendAnalysisResult | AlarmSummary
  let content: string

  if (data.type === 'COMPARE') {
    structured = {
      element: 'Z',
      summary: `演示对比：${DEMO.stations.qx.name} 水位 ${DEMO.qxWaterLevel} m，高于 ${DEMO.stations.bh.name} 11.82 m`,
      items: [
        {
          stationAddress: DEMO.stations.qx.code,
          observeTime: nowStr(),
          value: DEMO.qxWaterLevel,
          remark: DEMO.stations.qx.name,
        },
        {
          stationAddress: DEMO.stations.bh.code,
          observeTime: nowStr(),
          value: 11.82,
          remark: DEMO.stations.bh.name,
        },
        {
          stationAddress: DEMO.stations.level.code,
          observeTime: nowStr(),
          value: DEMO.levelAlert,
          remark: `${DEMO.stations.level.name}（超阈值）`,
        },
      ],
    }
    content = structured.summary
  } else if (data.type === 'TREND') {
    structured = {
      stationAddress: DEMO.stations.rain.code,
      element: 'P',
      startTime: daysAgoStr(0, 8),
      endTime: nowStr(),
      sampleCount: 4,
      min: 8.0,
      max: DEMO.rainDaily,
      avg: 28.4,
      sum: 113.6,
      trend: '上升',
      summary: `演示趋势：${DEMO.stations.rain.name} 今日雨量累计升至 ${DEMO.rainDaily} mm，呈上升趋势`,
      points: [
        { observeTime: '08:00', value: 8.0 },
        { observeTime: '12:00', value: 21.5 },
        { observeTime: '16:00', value: 41.0 },
        { observeTime: '20:00', value: DEMO.rainDaily },
      ],
    }
    content = structured.summary
  } else {
    structured = {
      level: 'WARN',
      totalCount: 2,
      summary: '演示告警：2 个未恢复告警（雨量二级 + 水位一级）',
      items: [
        {
          stationAddress: DEMO.stations.rain.code,
          element: 'P',
          currentValue: DEMO.rainDaily,
          threshold: DEMO.rainThreshold,
          observeTime: nowStr(),
          message: `${DEMO.stations.rain.name}雨量超过二级阈值`,
        },
        {
          stationAddress: DEMO.stations.level.code,
          element: 'Z',
          currentValue: DEMO.levelAlert,
          threshold: DEMO.levelThreshold,
          observeTime: nowStr(),
          message: `${DEMO.stations.level.name}水位超过一级阈值`,
        },
      ],
    }
    content = structured.summary
  }

  pushHistory('CHAT', data.message, content, 'demo-structured', undefined, conversationId)
  return {
    content,
    timestamp: nowStr(),
    model: 'demo-structured',
    conversationId,
    structured,
  }
}

export async function mockAiHealth(): Promise<string> {
  return 'UP（演示 Mock）'
}

export async function mockAiDiagnose(): Promise<string> {
  return (
    `演示模式诊断：AI 由前端 Mock 提供。台账对齐 ${DEMO.totalCount} 遥测站 / ` +
    `${DEMO.videoCount} 视频站 / ${DEMO.projectCount} 工程，知识文档 ${demoDocuments.filter((d) => d.processed).length} 份已向量化（模拟）。`
  )
}

export async function mockDocumentList(): Promise<DocumentVO[]> {
  await delay()
  return demoDocuments.map((d) => ({ ...d, content: undefined }))
}

export async function mockDocumentDetail(id: string): Promise<DocumentVO> {
  await delay()
  const doc = demoDocuments.find((item) => item.id === id)
  if (!doc) throw new Error('文档不存在')
  return { ...doc }
}

export async function mockDocumentUpload(params: DocumentUploadParams): Promise<DocumentVO> {
  await delay(800)
  const ext = params.file.name.includes('.')
    ? params.file.name.split('.').pop()?.toLowerCase() || 'bin'
    : 'bin'
  const doc: DocumentVO = {
    id: String(nextDocId++),
    fileName: params.file.name || `${params.title}.${ext}`,
    fileType: ext,
    fileSize: params.file.size,
    uploadTime: nowStr(),
    processed: false,
    categoryId: params.categoryId,
    categoryName: params.categoryId ? '演示知识库' : '通用知识库',
  }
  demoDocuments.unshift(doc)
  return { ...doc }
}

export async function mockDocumentDelete(id: string): Promise<void> {
  await delay()
  const index = demoDocuments.findIndex((item) => item.id === id)
  if (index >= 0) demoDocuments.splice(index, 1)
}

export async function mockDocumentReprocess(id: string): Promise<DocumentVO> {
  await delay(1000)
  const doc = demoDocuments.find((item) => item.id === id)
  if (!doc) throw new Error('文档不存在')
  doc.processed = true
  if (!doc.content) {
    doc.content = `（演示）已重新处理文档「${doc.fileName}」，生成模拟文本内容供知识问答检索。`
  }
  return { ...doc }
}

export async function mockDocumentQa(data: DocumentQueryDTO): Promise<ChatResponseVO> {
  await delay(700)
  const conversationId = ensureConversationId(data.conversationId)
  const content = buildDocumentQaReply(data.question, data.documentId, data.topK ?? 5)
  pushHistory(
    'DOCUMENT_QA',
    data.question,
    content,
    'demo-rag-mock',
    data.documentId,
    conversationId,
  )
  return {
    content,
    timestamp: nowStr(),
    model: 'demo-rag-mock',
    conversationId,
  }
}

export async function mockDocumentQaStream(
  data: DocumentQueryDTO,
  handlers: AiSseHandlers,
): Promise<void> {
  const conversationId = ensureConversationId(data.conversationId)
  const full = await mockDocumentQa({ ...data, conversationId })
  const text = full.content
  const chunkSize = 16
  for (let i = 0; i < text.length; i += chunkSize) {
    await delay(35)
    handlers.onMessage?.(text.slice(i, i + chunkSize))
  }
  handlers.onDone?.(conversationId)
}

export async function mockListQaHistory(scene: QaHistoryScene, limit = 200): Promise<QaHistoryVO[]> {
  await delay(200)
  return demoHistory
    .filter((item) => item.scene === scene)
    .slice(-limit)
    .map((item) => ({ ...item }))
}

export async function mockClearQaHistory(scene: QaHistoryScene): Promise<void> {
  await delay(150)
  for (let i = demoHistory.length - 1; i >= 0; i--) {
    if (demoHistory[i].scene === scene) demoHistory.splice(i, 1)
  }
}

export async function mockTruncateChatSession(
  conversationId: string,
  keepUserTurns: number,
): Promise<void> {
  await delay(80)
  const rows = demoHistory
    .map((item, index) => ({ item, index }))
    .filter(({ item }) => item.conversationId === conversationId)
  const keep = Math.max(0, keepUserTurns)
  const removeIndexes = rows.slice(keep).map(({ index }) => index)
  for (let i = removeIndexes.length - 1; i >= 0; i--) {
    demoHistory.splice(removeIndexes[i], 1)
  }
}
