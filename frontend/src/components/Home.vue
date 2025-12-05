<template>
  <div class="app-container">
    <el-container>
      <el-header>
        <div class="header-content">
          <h2>Kafka View</h2>
          <el-button type="danger" @click="handleLogout" style="position: absolute; right: 20px;">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <ClusterList @select="handleSelectCluster" />

        <el-dialog v-model="dialogVisible" title="Cluster Dashboard" width="80%" destroy-on-close>
           <div v-if="currentCluster">
             <h3>{{ currentCluster.name }} - Dashboard</h3>
             <ClusterDashboard :cluster-id="currentCluster.id" />
           </div>
        </el-dialog>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ClusterList from './ClusterList.vue'
import ClusterDashboard from './ClusterDashboard.vue'

const router = useRouter()
const dialogVisible = ref(false)
const currentCluster = ref(null)

const handleSelectCluster = (cluster) => {
  currentCluster.value = cluster
  dialogVisible.value = true
}

const handleLogout = () => {
  sessionStorage.removeItem('loggedIn')
  router.push('/login')
}

onMounted(() => {
  document.body.className = 'theme-minimal'
})
</script>

<style>
/* Removed body styles as they are now in theme.css */
.app-container {
  height: 100vh;
  /* Background is handled by body in theme.css */
}
.el-header {
  background-color: #ffffff !important;
  border-bottom: 1px solid #e6e6e6;
  color: #333;
  line-height: 60px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  position: relative;
}
/* Decorative line only for fantasy themes */
body.theme-souls .el-header::after,
body.theme-elden .el-header::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; width: 100%; height: 1px;
  background: linear-gradient(90deg, transparent, var(--app-primary), transparent);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  position: relative;
}
.header-content h2 {
  margin: 0;
  font-family: var(--app-font-header);
  text-transform: uppercase;
  letter-spacing: 4px;
  font-size: 2rem;
  color: #333;
  font-weight: 600;
  letter-spacing: 1px;
}
</style>