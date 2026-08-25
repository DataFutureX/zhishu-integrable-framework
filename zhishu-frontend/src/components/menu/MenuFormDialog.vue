<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="600px"
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <el-form ref="formRef" :model="formModel" :rules="rules" label-width="100px">
      <el-form-item label="上级菜单" prop="parentId">
        <el-tree-select
          v-model="formModel.parentId"
          :data="parentTreeOptions"
          node-key="id"
          :props="{ label: 'title', children: 'children' }"
          check-strictly
          :render-after-expand="false"
          placeholder="请选择上级菜单"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="菜单名称" prop="title">
        <el-input v-model="formModel.title" placeholder="请输入菜单名称" maxlength="50" />
      </el-form-item>
      <el-form-item label="菜单类型" prop="menuType">
        <el-select v-model="formModel.menuType" placeholder="请选择菜单类型" style="width: 100%">
          <el-option label="目录" value="DIRECTORY" />
          <el-option label="菜单" value="MENU" />
          <el-option label="页面" value="PAGE" />
          <el-option label="按钮" value="BUTTON" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="formModel.menuType !== 'BUTTON'" label="路径">
        <el-input v-model="formModel.path" placeholder="如 /permission/user" maxlength="200" />
      </el-form-item>
      <el-form-item
        :label="formModel.menuType === 'BUTTON' ? '权限标识' : '路由名称'"
        :prop="formModel.menuType === 'BUTTON' ? 'routeName' : undefined"
      >
        <el-input
          v-model="formModel.routeName"
          :placeholder="formModel.menuType === 'BUTTON' ? '如 system:user:query' : '如 User'"
          maxlength="100"
        />
      </el-form-item>
      <template v-if="formModel.menuType !== 'BUTTON'">
        <el-form-item label="重定向">
          <el-input v-model="formModel.redirect" placeholder="如 /permission/user" maxlength="200" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="formModel.icon" placeholder="Element Plus 图标名，如 SetUp" maxlength="50" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input
            v-model="formModel.component"
            placeholder="如 views/user/UserList.vue"
            maxlength="200"
          />
        </el-form-item>
        <el-form-item label="Meta">
          <el-input
            v-model="formModel.meta"
            type="textarea"
            :rows="2"
            placeholder='JSON 字符串，如 {"layout":"map"} 或 {"layout":"dashboard","hideTabBar":true}'
          />
        </el-form-item>
      </template>
      <el-form-item label="排序">
        <el-input-number v-model="formModel.sort" :min="0" :max="9999" controls-position="right" />
      </el-form-item>
      <el-form-item v-if="formModel.menuType !== 'BUTTON'" label="侧边栏可见">
        <el-radio-group v-model="formModel.visible">
          <el-radio :label="1">是</el-radio>
          <el-radio :label="0">否</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="需要登录">
        <el-radio-group v-model="formModel.requiresAuth">
          <el-radio :label="1">是</el-radio>
          <el-radio :label="0">否</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formModel.status">
          <el-radio :label="1">启用</el-radio>
          <el-radio :label="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="onSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { MenuFormModel } from '@/composables/useMenuList'

interface ParentTreeOption {
  id: number | string
  title: string
  children?: ParentTreeOption[]
}

defineProps<{
  title: string
  isCreate: boolean
  rules: FormRules
  parentTreeOptions: ParentTreeOption[]
  submitLoading?: boolean
}>()

const visible = defineModel<boolean>('visible', { required: true })
const formModel = defineModel<MenuFormModel>('form', { required: true })

const emit = defineEmits<{
  close: []
  submit: []
}>()

const formRef = ref<FormInstance>()

watch(visible, (open) => {
  if (!open) formRef.value?.clearValidate()
})

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (valid) emit('submit')
  })
}
</script>
