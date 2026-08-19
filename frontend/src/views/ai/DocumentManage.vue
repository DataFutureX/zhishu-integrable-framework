<template>
  <ListPageShell
    :loading="loading"
    :show-hero="true"
    :hero-title="heroTitle"
    :hero-eyebrow="inDocsView ? '知识库' : '数智中枢'"
    :hero-eyebrow-icon="FolderOpened"
    :hero-metrics="heroMetrics"
    :hero-enable-rate="inDocsView ? processedRate : undefined"
    hero-ring-label="已处理"
  >
    <template #heroDescription>
      <template v-if="inDocsView">
        共 <strong>{{ tableData.length }}</strong> 个文档，已完成向量化
        <strong>{{ processedCount }}</strong> 个
      </template>
      <template v-else>
        按主题分馆管理文档，上传后自动向量化，供知识检索与 Agent RAG 调用。
      </template>
    </template>
    <template #heroActions>
      <template v-if="inDocsView">
        <el-button size="small" :icon="ArrowLeft" @click="backToCatalog">返回知识库</el-button>
        <el-button type="primary" size="small" :icon="Upload" @click="openUploadDialog">
          上传文档
        </el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
      </template>
      <template v-else>
        <el-button type="primary" size="small" :icon="Plus" @click="openCreateCategory">新建知识库</el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
      </template>
    </template>

    <template v-if="inDocsView" #filter>
      <ListFilterPanel :active-count="activeFilterCount" :default-expanded="true">
        <el-form :inline="true" :model="queryParams" label-width="80px">
          <el-form-item label="文件名">
            <el-input
              v-model="queryParams.keyword"
              placeholder="按文件名搜索"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="queryParams.fileType" placeholder="全部" clearable style="width: 120px">
              <el-option v-for="type in fileTypeOptions" :key="type" :label="type.toUpperCase()" :value="type" />
            </el-select>
          </el-form-item>
          <el-form-item label="向量化">
            <el-select v-model="processedFilter" placeholder="全部" clearable style="width: 120px">
              <el-option label="已处理" :value="true" />
              <el-option label="处理中" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </ListFilterPanel>
    </template>

    <!-- 首页：知识库馆藏目录（页主体） -->
    <div v-if="!inDocsView" class="kb-home">
      <div class="kb-toolbar">
        <el-input
          v-model="catalogKeyword"
          class="kb-toolbar__search"
          clearable
          placeholder="搜索知识库名称、编码或描述…"
          :prefix-icon="Search"
        />
        <el-radio-group v-model="catalogStatus" size="default">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="ENABLED">启用</el-radio-button>
          <el-radio-button value="DISABLED">停用</el-radio-button>
        </el-radio-group>
      </div>

      <el-empty
        v-if="!filteredCatalog.length"
        class="kb-empty"
        :description="catalogKeyword || catalogStatus !== 'ALL' ? '没有匹配的知识库' : '暂无知识库，请先新建'"
        :image-size="88"
      >
        <el-button v-if="!catalogKeyword && catalogStatus === 'ALL'" type="primary" :icon="Plus" @click="openCreateCategory">
          新建知识库
        </el-button>
      </el-empty>

      <div v-else class="kb-shelf">
        <article
          v-for="(cat, index) in filteredCatalog"
          :key="cat.id"
          class="kb-collection"
          :class="{
            'kb-collection--enabled': cat.status === 'ENABLED',
            'kb-collection--disabled': cat.status !== 'ENABLED',
          }"
          role="button"
          tabindex="0"
          @click="enterCategory(cat)"
          @keydown.enter="enterCategory(cat)"
        >
          <div
            class="kb-collection__spine"
            :data-tone="index % 5"
            :title="`${cat.documentCount ?? 0} 篇文档`"
            aria-hidden="true"
          >
            <span class="kb-collection__initial">{{ categoryInitial(cat.name) }}</span>
          </div>
          <div class="kb-collection__body">
            <header class="kb-collection__head">
              <h3 class="kb-collection__name" :title="cat.name">{{ cat.name }}</h3>
              <div class="kb-collection__head-side" @click.stop>
                <el-tag
                  size="small"
                  :type="cat.status === 'ENABLED' ? 'success' : 'info'"
                  effect="plain"
                  round
                >
                  {{ cat.status === 'ENABLED' ? '启用' : '停用' }}
                </el-tag>
                <el-dropdown trigger="click" @command="(cmd) => onCategoryCommand(cmd, cat)">
                  <el-button class="kb-collection__more" text size="small" :icon="MoreFilled" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>
                        <span class="kb-collection__danger">删除</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </header>
            <p class="kb-collection__desc" :title="cat.description || ''">
              {{ cat.description || '暂无馆藏说明，可在编辑中补充主题与用途。' }}
            </p>
            <footer class="kb-collection__foot">
              <span class="kb-collection__docs">
                <el-icon><Document /></el-icon>
                {{ cat.documentCount ?? 0 }} 篇
              </span>
              <span class="kb-collection__enter" aria-hidden="true">
                <el-icon><ArrowRight /></el-icon>
              </span>
            </footer>
          </div>
        </article>
      </div>
    </div>

    <!-- 详情：文档列表 -->
    <el-table
      v-else
      :data="displayData"
      class="modern-table"
      empty-text="该知识库暂无文档，请先上传"
    >
      <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column prop="fileType" label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ (row.fileType || '-').toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="110" align="right">
        <template #default="{ row }">
          {{ formatFileSize(row.fileSize ?? 0) }}
        </template>
      </el-table-column>
      <el-table-column label="向量化" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.processed ? 'success' : 'warning'" size="small">
            {{ row.processed ? '已处理' : '处理中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.uploadTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDetail(row as DocumentVO)">查看</el-button>
          <el-button
            type="warning"
            link
            size="small"
            :loading="reprocessingId === row.id"
            @click="handleReprocess(row as DocumentVO)"
          >
            重新处理
          </el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row as DocumentVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #extra>
      <el-dialog
        v-model="uploadVisible"
        title="上传文档"
        width="520px"
        :close-on-click-modal="false"
        @close="resetUploadForm"
      >
        <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="88px">
          <el-form-item label="标题" prop="title">
            <el-input v-model="uploadForm.title" placeholder="文档标题（必填）" maxlength="100" />
          </el-form-item>
          <el-form-item label="知识库" prop="categoryId">
            <el-select
              v-model="uploadForm.categoryId"
              placeholder="选择知识库分类"
              style="width: 100%"
              :disabled="inDocsView"
            >
              <el-option
                v-for="cat in enabledCategories"
                :key="cat.id"
                :label="cat.name"
                :value="cat.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="文件" prop="file">
            <el-upload
              drag
              :auto-upload="false"
              :limit="1"
              :file-list="fileList"
              accept=".pdf,.doc,.docx,.txt,.md,.xlsx,.xls,.csv"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :on-exceed="() => ElMessage.warning('一次仅支持上传一个文件')"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 pdf / doc / docx / txt / md / xlsx 等，建议不超过 50MB</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">确定上传</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="categoryFormVisible"
        :title="categoryForm.id ? '编辑知识库' : '新建知识库'"
        width="520px"
        :close-on-click-modal="false"
        destroy-on-close
        @closed="resetCategoryForm"
      >
        <el-form
          ref="categoryFormRef"
          :model="categoryForm"
          :rules="categoryRules"
          label-width="80px"
        >
          <el-form-item v-if="!categoryForm.id" label="编码" prop="code">
            <el-input v-model="categoryForm.code" placeholder="英文/数字，如 hydrology" maxlength="64" />
          </el-form-item>
          <el-form-item label="名称" prop="name">
            <el-input v-model="categoryForm.name" placeholder="知识库名称" maxlength="128" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="categoryForm.description" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="categoryForm.sortOrder" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item v-if="categoryForm.id" label="状态">
            <el-select v-model="categoryForm.status" style="width: 160px">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="categoryFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="categorySaving" @click="submitCategory">保存</el-button>
        </template>
      </el-dialog>

      <el-drawer
        v-model="detailVisible"
        direction="rtl"
        size="640px"
        destroy-on-close
        class="kb-detail-drawer"
      >
        <template #header>
          <div class="drawer-header">
            <div class="drawer-header-main">
              <div class="drawer-header-icon" :class="{ 'is-online': detailDoc?.processed }">
                <el-icon :size="22"><Document /></el-icon>
              </div>
              <div class="drawer-header-text">
                <div class="drawer-eyebrow">文档详情</div>
                <div class="drawer-title">{{ detailDoc?.fileName || '加载中…' }}</div>
                <div class="drawer-subtitle">
                  <span v-if="detailDoc" class="drawer-code">ID {{ detailDoc.id }}</span>
                  <el-tag
                    v-if="detailDoc?.categoryName"
                    size="small"
                    type="info"
                    effect="plain"
                    round
                  >
                    {{ detailDoc.categoryName }}
                  </el-tag>
                  <el-tag
                    v-if="detailDoc"
                    size="small"
                    :type="detailDoc.processed ? 'success' : 'warning'"
                    effect="plain"
                    round
                  >
                    {{ detailDoc.processed ? '已向量化' : '处理中' }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
        </template>

        <div v-loading="detailLoading" class="drawer-body">
          <template v-if="detailDoc">
            <div class="drawer-summary">
              <div class="summary-card summary-card--primary">
                <div class="summary-card__icon">
                  <el-icon :size="18"><FolderOpened /></el-icon>
                </div>
                <div class="summary-card__content">
                  <span class="summary-card__label">知识库</span>
                  <span class="summary-card__value summary-card__value--ellipsis">
                    {{ detailDoc.categoryName || '未分类' }}
                  </span>
                </div>
              </div>
              <div class="summary-card">
                <div class="summary-card__icon">
                  <el-icon :size="18"><Document /></el-icon>
                </div>
                <div class="summary-card__content">
                  <span class="summary-card__label">类型 / 大小</span>
                  <span class="summary-card__value">
                    {{ (detailDoc.fileType || '-').toUpperCase() }} · {{ formatFileSize(detailDoc.fileSize ?? 0) }}
                  </span>
                </div>
              </div>
              <div class="summary-card summary-card--time">
                <div class="summary-card__icon">
                  <el-icon :size="18"><Clock /></el-icon>
                </div>
                <div class="summary-card__content">
                  <span class="summary-card__label">上传时间</span>
                  <span class="summary-card__value">{{ formatDateTime(detailDoc.uploadTime) }}</span>
                </div>
              </div>
            </div>

            <el-card shadow="never" class="drawer-section-card">
              <template #header>
                <div class="section-card-header">
                  <span class="section-card-title">基本信息</span>
                </div>
              </template>
              <el-descriptions :column="1" border class="drawer-descriptions">
                <el-descriptions-item label="文档 ID">{{ detailDoc.id }}</el-descriptions-item>
                <el-descriptions-item label="文件名">{{ detailDoc.fileName }}</el-descriptions-item>
                <el-descriptions-item label="知识库">{{ detailDoc.categoryName || '未分类' }}</el-descriptions-item>
                <el-descriptions-item label="向量化">
                  <el-tag :type="detailDoc.processed ? 'success' : 'warning'" size="small" effect="plain">
                    {{ detailDoc.processed ? '已处理' : '处理中' }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never" class="drawer-section-card">
              <template #header>
                <div class="section-card-header">
                  <span class="section-card-title">文本内容</span>
                  <span class="section-meta">
                    {{ detailDoc.content ? `${detailDoc.content.length.toLocaleString()} 字` : '暂无解析内容' }}
                  </span>
                </div>
              </template>
              <el-scrollbar max-height="calc(100vh - 420px)">
                <pre v-if="detailDoc.content" class="doc-content-text">{{ detailDoc.content }}</pre>
                <el-empty v-else description="该文档暂无可用文本内容" :image-size="72" />
              </el-scrollbar>
            </el-card>
          </template>
        </div>
      </el-drawer>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIDocumentManage' })

import { computed, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type UploadFile,
  type UploadUserFile,
} from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Collection,
  FolderOpened,
  MoreFilled,
  Plus,
  Refresh,
  Search,
  Upload,
  UploadFilled,
  Document,
  CircleCheck,
  Clock,
} from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import ListFilterPanel from '@/components/list-page/ListFilterPanel.vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import {
  createKnowledgesCategory,
  deleteDocument,
  deleteKnowledgesCategory,
  getKnowledgesCategoryList,
  getDocumentDetail,
  getDocumentList,
  reprocessDocument,
  updateKnowledgesCategory,
  uploadDocument,
} from '@/api/ai'
import type { KnowledgesCategoryVO, DocumentVO } from '@/types/aiDocument'
import { formatDateTime, formatFileSize } from '@/utils/format'

const loading = ref(false)
const uploading = ref(false)
const reprocessingId = ref<string | null>(null)
const tableData = ref<DocumentVO[]>([])
const categories = ref<KnowledgesCategoryVO[]>([])
const selectedCategoryId = ref<string | undefined>(undefined)
const processedFilter = ref<boolean | undefined>(undefined)
const catalogKeyword = ref('')
const catalogStatus = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')
const queryParams = reactive({
  keyword: '',
  fileType: '' as string,
})

const uploadVisible = ref(false)
const categoryFormVisible = ref(false)
const categorySaving = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailDoc = ref<DocumentVO | null>(null)
const uploadFormRef = ref<FormInstance>()
const categoryFormRef = ref<FormInstance>()
const fileList = ref<UploadUserFile[]>([])
const uploadForm = reactive<{
  title: string
  categoryId: string
  file: File | null
}>({
  title: '',
  categoryId: '',
  file: null,
})

const categoryForm = reactive({
  id: '' as string,
  code: '',
  name: '',
  description: '',
  sortOrder: 100,
  status: 'ENABLED',
})

const uploadRules: FormRules = {
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择知识库', trigger: 'change' }],
  file: [
    {
      required: true,
      validator: (_rule, _value, callback) => {
        if (!uploadForm.file) callback(new Error('请选择文件'))
        else callback()
      },
      trigger: 'change',
    },
  ],
}

const categoryRules: FormRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const enabledCategories = computed(() =>
  categories.value.filter((c) => c.status === 'ENABLED'),
)

const catalogCategories = computed(() =>
  [...categories.value].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)),
)

const filteredCatalog = computed(() => {
  const kw = catalogKeyword.value.trim().toLowerCase()
  return catalogCategories.value.filter((cat) => {
    if (catalogStatus.value === 'ENABLED' && cat.status !== 'ENABLED') return false
    if (catalogStatus.value === 'DISABLED' && cat.status === 'ENABLED') return false
    if (!kw) return true
    const hay = `${cat.name || ''} ${cat.code || ''} ${cat.description || ''}`.toLowerCase()
    return hay.includes(kw)
  })
})

/** 书脊首字 */
function categoryInitial(name?: string | null): string {
  const t = (name || '').trim()
  return t ? t.slice(0, 1).toUpperCase() : '?'
}

function onCategoryCommand(cmd: string | number | object, cat: KnowledgesCategoryVO) {
  if (cmd === 'edit') openEditCategory(cat)
  else if (cmd === 'delete') handleDeleteCategory(cat)
}

const categoryDocTotal = computed(() =>
  categories.value.reduce((sum, c) => sum + (c.documentCount ?? 0), 0),
)

const inDocsView = computed(() => !!selectedCategoryId.value)

const currentCategory = computed(() =>
  categories.value.find((c) => c.id === selectedCategoryId.value),
)

const heroTitle = computed(() =>
  inDocsView.value ? currentCategory.value?.name || '知识库文档' : '知识库',
)

const processedCount = computed(() => tableData.value.filter((item) => item.processed).length)

const processedRate = computed(() => {
  if (!tableData.value.length) return 0
  return Math.round((processedCount.value / tableData.value.length) * 100)
})

const heroMetrics = computed(() => {
  if (inDocsView.value) {
    return [
      {
        key: 'total',
        label: '文档总数',
        value: tableData.value.length,
        icon: Document,
        accent: 'primary' as const,
      },
      {
        key: 'processed',
        label: '已处理',
        value: processedCount.value,
        icon: CircleCheck,
        accent: 'success' as const,
      },
      {
        key: 'pending',
        label: '处理中',
        value: tableData.value.length - processedCount.value,
        icon: Clock,
        accent: 'danger' as const,
      },
    ]
  }
  return [
    {
      key: 'kb',
      label: '知识库数',
      value: categories.value.length,
      icon: Collection,
      accent: 'primary' as const,
    },
    {
      key: 'docs',
      label: '文档合计',
      value: categoryDocTotal.value,
      icon: Document,
      accent: 'primary' as const,
    },
  ]
})

const fileTypeOptions = computed(() => {
  const set = new Set<string>()
  tableData.value.forEach((item) => {
    if (item.fileType) set.add(item.fileType.toLowerCase())
  })
  return [...set].sort()
})

const activeFilterCount = computed(() => {
  let count = 0
  if (queryParams.keyword.trim()) count += 1
  if (queryParams.fileType) count += 1
  if (processedFilter.value !== undefined) count += 1
  return count
})

const displayData = computed(() => {
  const keyword = queryParams.keyword.trim().toLowerCase()
  return tableData.value.filter((item) => {
    if (processedFilter.value !== undefined && item.processed !== processedFilter.value) return false
    if (queryParams.fileType && item.fileType?.toLowerCase() !== queryParams.fileType.toLowerCase()) {
      return false
    }
    if (keyword && !item.fileName?.toLowerCase().includes(keyword)) return false
    return true
  })
})

const fetchCategories = async () => {
  try {
    categories.value = await getKnowledgesCategoryList(true)
  } catch (error) {
    console.error('获取知识库分类失败:', error)
  }
}

const fetchList = async () => {
  if (!selectedCategoryId.value) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    tableData.value = await getDocumentList(selectedCategoryId.value)
  } catch (error) {
    console.error('获取文档列表失败:', error)
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  loading.value = true
  try {
    await fetchCategories()
    if (selectedCategoryId.value) {
      const stillExists = categories.value.some((c) => c.id === selectedCategoryId.value)
      if (!stillExists) {
        selectedCategoryId.value = undefined
        tableData.value = []
        return
      }
      tableData.value = await getDocumentList(selectedCategoryId.value)
    }
  } catch (error) {
    console.error('刷新知识库失败:', error)
  } finally {
    loading.value = false
  }
}

const enterCategory = (cat: KnowledgesCategoryVO) => {
  selectedCategoryId.value = cat.id
  queryParams.keyword = ''
  queryParams.fileType = ''
  processedFilter.value = undefined
  void fetchList()
}

const backToCatalog = () => {
  selectedCategoryId.value = undefined
  tableData.value = []
  queryParams.keyword = ''
  queryParams.fileType = ''
  processedFilter.value = undefined
  void fetchCategories()
}

const handleSearch = () => {
  /* 前端过滤，computed 自动更新 */
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.fileType = ''
  processedFilter.value = undefined
}

const openUploadDialog = () => {
  resetUploadForm()
  if (!uploadForm.categoryId && selectedCategoryId.value) {
    uploadForm.categoryId = selectedCategoryId.value
  } else if (!uploadForm.categoryId && enabledCategories.value.length) {
    uploadForm.categoryId = enabledCategories.value[0].id
  }
  uploadVisible.value = true
}

const resetUploadForm = () => {
  uploadForm.title = ''
  uploadForm.categoryId = selectedCategoryId.value || enabledCategories.value[0]?.id || ''
  uploadForm.file = null
  fileList.value = []
  uploadFormRef.value?.clearValidate()
}

const handleFileChange = (file: UploadFile) => {
  uploadForm.file = file.raw ?? null
  fileList.value = file.raw ? [file] : []
  if (!uploadForm.title.trim() && file.name) {
    uploadForm.title = file.name.replace(/\.[^.]+$/, '')
  }
  uploadFormRef.value?.validateField('file')
}

const handleFileRemove = () => {
  uploadForm.file = null
  fileList.value = []
}

const submitUpload = async () => {
  if (!uploadFormRef.value) return
  await uploadFormRef.value.validate(async (valid) => {
    if (!valid || !uploadForm.file) return
    uploading.value = true
    try {
      await uploadDocument({
        title: uploadForm.title.trim(),
        categoryId: uploadForm.categoryId || undefined,
        file: uploadForm.file,
      })
      ElMessage.success('上传成功，系统将进行向量化处理')
      uploadVisible.value = false
      await refreshAll()
    } catch (error) {
      console.error('上传文档失败:', error)
    } finally {
      uploading.value = false
    }
  })
}

const openCreateCategory = () => {
  resetCategoryForm()
  categoryFormVisible.value = true
}

const openEditCategory = (row: KnowledgesCategoryVO) => {
  categoryForm.id = row.id
  categoryForm.code = row.code
  categoryForm.name = row.name
  categoryForm.description = row.description || ''
  categoryForm.sortOrder = row.sortOrder ?? 100
  categoryForm.status = row.status || 'ENABLED'
  categoryFormVisible.value = true
}

const resetCategoryForm = () => {
  categoryForm.id = ''
  categoryForm.code = ''
  categoryForm.name = ''
  categoryForm.description = ''
  categoryForm.sortOrder = 100
  categoryForm.status = 'ENABLED'
  categoryFormRef.value?.clearValidate()
}

const submitCategory = async () => {
  if (!categoryFormRef.value) return
  await categoryFormRef.value.validate(async (valid) => {
    if (!valid) return
    categorySaving.value = true
    try {
      if (categoryForm.id) {
        await updateKnowledgesCategory(categoryForm.id, {
          name: categoryForm.name.trim(),
          description: categoryForm.description.trim() || undefined,
          sortOrder: categoryForm.sortOrder,
          status: categoryForm.status,
        })
        ElMessage.success('知识库已更新')
      } else {
        await createKnowledgesCategory({
          code: categoryForm.code.trim(),
          name: categoryForm.name.trim(),
          description: categoryForm.description.trim() || undefined,
          sortOrder: categoryForm.sortOrder,
        })
        ElMessage.success('知识库已创建')
      }
      categoryFormVisible.value = false
      await refreshAll()
    } catch (error) {
      console.error('保存知识库失败:', error)
    } finally {
      categorySaving.value = false
    }
  })
}

const handleDeleteCategory = async (row: KnowledgesCategoryVO) => {
  try {
    await ElMessageBox.confirm(
      `确认删除知识库「${row.name}」？仅当无文档时可删除。`,
      '删除知识库',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteKnowledgesCategory(row.id)
    ElMessage.success('已删除')
    if (selectedCategoryId.value === row.id) {
      selectedCategoryId.value = undefined
      tableData.value = []
    }
    await refreshAll()
  } catch (error) {
    console.error('删除知识库失败:', error)
  }
}

const openDetail = async (row: DocumentVO) => {
  detailVisible.value = true
  detailDoc.value = null
  detailLoading.value = true
  try {
    detailDoc.value = await getDocumentDetail(row.id)
  } catch (error) {
    console.error('获取文档详情失败:', error)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleReprocess = async (row: DocumentVO) => {
  try {
    await ElMessageBox.confirm(`确认重新处理文档「${row.fileName}」？`, '重新处理', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  reprocessingId.value = row.id
  try {
    await reprocessDocument(row.id)
    ElMessage.success('已提交重新处理')
    await fetchList()
  } catch (error) {
    console.error('重新处理失败:', error)
  } finally {
    reprocessingId.value = null
  }
}

const handleDelete = async (row: DocumentVO) => {
  try {
    await ElMessageBox.confirm(`确认删除文档「${row.fileName}」？此操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    await refreshAll()
  } catch (error) {
    console.error('删除文档失败:', error)
  }
}

useRouteActivate(refreshAll)
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.kb-home {
  --kb-ink: #1a2b3c;
  --kb-muted: #5c6b7a;
  --kb-line: rgba(26, 43, 60, 0.1);
  --kb-shelf: #f4f7fa;
  --kb-accent: #0e7490;
  padding: 14px 12px 16px;
  min-height: 360px;
}

.kb-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--kb-line);
  border-radius: var(--app-radius-md);
  background: var(--kb-shelf);

  &__search {
    flex: 1;
    min-width: 200px;
    max-width: 360px;
  }
}

.kb-empty {
  padding: 48px 0;
}

.kb-shelf {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}

.kb-collection {
  position: relative;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  min-height: 148px;
  border-radius: 12px;
  border: 1px solid rgba(26, 43, 60, 0.08);
  background: #fff;
  overflow: hidden;
  cursor: pointer;
  text-align: left;
  padding: 0;
  font: inherit;
  color: inherit;
  outline: none;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover,
  &:focus-visible {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--kb-accent) 35%, transparent);
    box-shadow: 0 4px 8px rgba(15, 23, 42, 0.05), 0 12px 24px rgba(15, 23, 42, 0.07);

    .kb-collection__enter {
      color: var(--kb-accent);
      transform: translateX(2px);
    }
  }

  &--disabled {
    opacity: 0.78;
  }

  &__spine {
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, #5eead4, #14b8a6);

    &[data-tone='1'] {
      background: linear-gradient(180deg, #7dd3fc, #38bdf8);
    }
    &[data-tone='2'] {
      background: linear-gradient(180deg, #fdba74, #fb923c);
    }
    &[data-tone='3'] {
      background: linear-gradient(180deg, #94a3b8, #64748b);
    }
    &[data-tone='4'] {
      background: linear-gradient(180deg, #6ee7b7, #34d399);
    }
  }

  &__initial {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 0.02em;
    color: rgba(255, 255, 255, 0.95);
    user-select: none;
    text-shadow: 0 1px 1px rgba(15, 23, 42, 0.12);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px 14px 12px;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  &__head-side {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
  }

  &__more {
    margin-left: 0;
    color: var(--kb-muted);
  }

  &__danger {
    color: var(--el-color-danger);
  }

  &__name {
    flex: 1;
    min-width: 0;
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    line-height: 1.35;
    color: var(--kb-ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__desc {
    margin: 0;
    min-height: 40px;
    font-size: 13px;
    line-height: 1.55;
    color: var(--kb-muted);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: auto;
    padding-top: 6px;
  }

  &__docs {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 550;
    color: var(--kb-muted);
  }

  &__enter {
    display: inline-flex;
    align-items: center;
    margin-left: auto;
    font-size: 16px;
    color: var(--kb-muted);
    transition: color 0.15s ease, transform 0.15s ease;
  }
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 8px;

  .drawer-header-main {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
  }

  .drawer-header-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: 12px;
    background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
    color: #fff;
    flex-shrink: 0;
    box-shadow: 0 4px 14px rgba(64, 158, 255, 0.35);

    &.is-online {
      background: linear-gradient(135deg, #67c23a 0%, #4ea82a 100%);
      box-shadow: 0 4px 14px rgba(103, 194, 58, 0.38);
    }
  }

  .drawer-header-text {
    min-width: 0;
  }

  .drawer-eyebrow {
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: $primary-color;
    margin-bottom: 2px;
  }

  .drawer-title {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.35;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .drawer-subtitle {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    flex-wrap: wrap;
  }

  .drawer-code {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    color: $text-secondary;
    padding: 2px 8px;
    border-radius: 4px;
    background: rgba(64, 158, 255, 0.06);
    border: 1px solid rgba(64, 158, 255, 0.12);
  }
}

.drawer-body {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 16px 20px 24px;
  background:
    radial-gradient(ellipse at top right, rgba(64, 158, 255, 0.06) 0%, transparent 55%),
    $bg-color;
}

.drawer-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;

  .summary-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    border-radius: $border-radius-md;
    border: 1px solid $border-lighter;
    background: $bg-white;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

    &__icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: rgba(144, 147, 153, 0.12);
      color: $info-color;
      flex-shrink: 0;
    }

    &__content {
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    &__label {
      font-size: 12px;
      color: $text-secondary;
    }

    &__value {
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
      line-height: 1.3;

      &--ellipsis {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    &--success .summary-card__icon {
      background: rgba(103, 194, 58, 0.12);
      color: $success-color;
    }

    &--primary .summary-card__icon {
      background: rgba(64, 158, 255, 0.12);
      color: $primary-color;
    }

    &--time .summary-card__icon {
      background: rgba(64, 158, 255, 0.12);
      color: $primary-color;
    }
  }
}

.drawer-section-card {
  margin-bottom: 16px;
  border-radius: $border-radius-md;
  border: 1px solid $border-lighter;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;

  &:last-child {
    margin-bottom: 0;
  }

  :deep(.el-card__header) {
    padding: 12px 16px;
    border-bottom: 1px solid $border-lighter;
    background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  }

  :deep(.el-card__body) {
    padding: 16px;
    background: $bg-white;
  }
}

.section-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-card-title {
  position: relative;
  padding-left: 10px;
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 3px;
    height: 14px;
    border-radius: 2px;
    background: $bg-gradient;
  }
}

.section-meta {
  font-size: 12px;
  color: $text-secondary;
}

.section-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
}

.drawer-descriptions {
  :deep(.el-descriptions__label) {
    width: 120px;
    font-weight: 500;
    color: $text-regular;
    background: #f8fafc;
  }

  :deep(.el-descriptions__content) {
    color: $text-primary;
    font-weight: 500;
  }
}

.doc-content-text {
  margin: 0;
  padding: 4px 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: $text-regular;
}
</style>

<style lang="scss">
@use '@/styles/variables.scss' as *;

.kb-detail-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 18px 20px 16px;
    border-bottom: 1px solid $border-lighter;
    background: linear-gradient(180deg, #f8fbff 0%, #fff 100%);
    position: relative;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: linear-gradient(90deg, #409eff 0%, #66b1ff 50%, #409eff 100%);
    }
  }

  .el-drawer__body {
    padding: 0;
    background: $bg-color;
    overflow: auto;
  }

  .el-drawer__close-btn {
    font-size: 18px;
    width: 32px;
    height: 32px;
    border-radius: 8px;

    &:hover {
      color: $primary-color;
      background: rgba(64, 158, 255, 0.08);
    }
  }
}
</style>
