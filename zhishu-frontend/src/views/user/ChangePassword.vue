<template>
  <div class="change-password-container">
    <el-card class="password-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改密码</span>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="password-tip"
        title="为保障账号安全，修改密码后需重新登录。"
      />

      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
        class="password-form"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码（6-20位）"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">
            确认修改
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePasswordApi } from '@/api/user'
import { logoutAndRedirect } from '@/utils/logout'

const router = useRouter()

const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
}

const handleReset = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    changingPassword.value = true
    try {
      await changePasswordApi({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })

      ElMessage.success('密码修改成功，请重新登录')
      handleReset()

      setTimeout(() => {
        // 改密后服务端已吊销 Token，仅清理本地并跳转登录页
        void logoutAndRedirect(router, { silent: true, notifyServer: false })
      }, 1500)
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '密码修改失败'
      ElMessage.error(message)
    } finally {
      changingPassword.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.change-password-container {
  width: 100%;
}

.password-card {
  width: 100%;

  :deep(.el-card__header) {
    padding: 16px 20px;
    border-bottom: 1px solid #d0d7de;
  }

  :deep(.el-card__body) {
    padding: 24px 20px;
  }

  .card-header {
    font-size: 16px;
    font-weight: 600;
  }
}

.password-tip {
  margin-bottom: 20px;
  max-width: 560px;
}

.password-form {
  max-width: 560px;
}
</style>
