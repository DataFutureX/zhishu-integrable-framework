<template>
  <div class="profile-container">
    <el-card v-loading="loading" class="profile-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>个人信息</span>
        </div>
      </template>

      <div class="user-info-section">
        <el-avatar :size="80" class="user-avatar">
          {{ userStore.userName?.charAt(0) || 'U' }}
        </el-avatar>
        <div class="user-details">
          <h3>{{ userStore.userName }}</h3>
          <p class="user-role">{{ userStore.userRole || '未分配角色' }}</p>
        </div>
      </div>

      <el-divider />

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
        class="profile-form"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" disabled />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入真实姓名" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱地址" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号码" />
        </el-form-item>

        <el-form-item label="角色">
          <el-tag :type="userStore.isAdmin ? 'danger' : 'primary'">
            {{ userStore.userRole || '未分配角色' }}
          </el-tag>
        </el-form-item>

        <el-form-item label="状态">
          <el-tag :type="userStore.userInfo?.status === 1 ? 'success' : 'info'">
            {{ userStore.userInfo?.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="updating" @click="handleUpdate">
            保存修改
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getCurrentUserApi, updateCurrentProfileApi } from '@/api/user'
import { useUserStore } from '@/stores/useUserStore'

const userStore = useUserStore()

const formRef = ref<FormInstance>()
const updating = ref(false)
const loading = ref(false)

const formData = reactive({
  username: '',
  realName: '',
  email: '',
  phone: '',
})

const formRules: FormRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }],
}

const initFormData = () => {
  if (!userStore.userInfo) return
  formData.username = userStore.userInfo.username
  formData.realName = userStore.userInfo.realName
  formData.email = userStore.userInfo.email
  formData.phone = userStore.userInfo.phone
}

const fetchUserDetail = async () => {
  loading.value = true
  try {
    const userData = await getCurrentUserApi()
    userStore.setUserInfo(userData)
    initFormData()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '获取用户详情失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

const handleUpdate = async () => {
  if (!formRef.value || !userStore.userInfo) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    updating.value = true
    try {
      const updated = await updateCurrentProfileApi({
        realName: formData.realName,
        email: formData.email,
        phone: formData.phone,
      })

      userStore.setUserInfo(updated)
      initFormData()
      ElMessage.success('保存成功')
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '保存失败'
      ElMessage.error(message)
    } finally {
      updating.value = false
    }
  })
}

const handleReset = () => {
  initFormData()
  formRef.value?.clearValidate()
}

onMounted(() => {
  fetchUserDetail()
})
</script>

<style lang="scss" scoped>
.profile-container {
  width: 100%;
}

.profile-card {
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

  .user-info-section {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 8px 0 4px;

    .user-avatar {
      background-color: var(--app-primary);
      font-size: 32px;
      font-weight: bold;
    }

    .user-details {
      h3 {
        margin: 0 0 8px;
        font-size: 20px;
        color: #303133;
      }

      .user-role {
        margin: 0;
        font-size: 14px;
        color: #909399;
      }
    }
  }

  .profile-form {
    margin-top: 8px;
    max-width: 560px;
  }
}
</style>
