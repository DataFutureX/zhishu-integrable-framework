<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="600px"
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <el-form ref="formRef" :model="formModel" :rules="rules" label-width="100px">
      <el-form-item label="上级单位" prop="parentId">
        <el-tree-select
          v-model="formModel.parentId"
          :data="formParentTreeOptions"
          node-key="id"
          :props="{ label: 'unitName', children: 'children' }"
          check-strictly
          :render-after-expand="false"
          placeholder="不选则为顶级单位"
          clearable
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="单位名称" prop="unitName">
        <el-input v-model="formModel.unitName" placeholder="请输入单位名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="单位编码" prop="unitCode">
        <el-input
          v-model="formModel.unitCode"
          placeholder="选填，不填则由系统生成"
          :disabled="!isCreate"
          maxlength="64"
        />
      </el-form-item>
      <el-form-item label="单位类型">
        <el-input v-model="formModel.unitType" placeholder="如：管理单位、运维单位" maxlength="50" />
      </el-form-item>
      <el-form-item label="所属区域">
        <el-input v-model="formModel.region" placeholder="请输入所属区域" maxlength="100" />
      </el-form-item>
      <el-form-item label="详细地址">
        <el-input v-model="formModel.address" placeholder="请输入详细地址" maxlength="200" />
      </el-form-item>
      <el-form-item label="联系人">
        <el-input v-model="formModel.contactPerson" placeholder="请输入联系人" maxlength="50" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="formModel.contactPhone" placeholder="请输入联系电话" maxlength="20" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="formModel.sort" :min="0" :max="9999" controls-position="right" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formModel.status">
          <el-radio :label="1">启用</el-radio>
          <el-radio :label="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="formModel.remark"
          type="textarea"
          :rows="2"
          placeholder="请输入备注"
          maxlength="500"
          show-word-limit
        />
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
import type { UnitVO } from '@/types/unit'
import type { UnitFormModel } from '@/composables/useUnitList'

defineProps<{
  title: string
  isCreate: boolean
  rules: FormRules
  formParentTreeOptions: UnitVO[]
  submitLoading?: boolean
}>()

const visible = defineModel<boolean>('visible', { required: true })
const formModel = defineModel<UnitFormModel>('form', { required: true })

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
