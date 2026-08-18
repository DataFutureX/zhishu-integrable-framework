<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="560px"
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <el-form ref="formRef" :model="formModel" :rules="rules" label-width="90px">
      <el-form-item v-if="!isCreate" label="用户ID">
        <el-input :model-value="String(formModel.id ?? '')" disabled placeholder="系统自动生成" />
      </el-form-item>
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="formModel.username"
          placeholder="请输入用户名（3-20个字符）"
          :disabled="!isCreate"
          maxlength="20"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="真实姓名" prop="realName">
        <el-input v-model="formModel.realName" placeholder="请输入真实姓名" maxlength="50" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="formModel.email" placeholder="请输入邮箱地址" maxlength="100" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="formModel.phone" placeholder="请输入11位手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="formModel.password"
          type="password"
          :placeholder="isCreate ? '请输入密码（6-20位）' : '留空则不修改密码'"
          show-password
          maxlength="20"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="角色" prop="roleId">
        <el-select v-model="formModel.roleId" placeholder="请选择角色" style="width: 100%">
          <el-option
            v-for="role in roleOptions"
            :key="role.id"
            :label="role.roleName"
            :value="role.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
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
import type { RoleVO } from '@/types/role'
import type { UserFormModel } from '@/composables/useUserList'

defineProps<{
  title: string
  isCreate: boolean
  rules: FormRules
  roleOptions: RoleVO[]
  submitLoading?: boolean
}>()

const visible = defineModel<boolean>('visible', { required: true })
const formModel = defineModel<UserFormModel>('form', { required: true })

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
