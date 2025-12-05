<template>
  <div class="dashboard" v-loading="loading">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="Overview" name="overview">
        <div class="header-row">
          <div class="title">{{ clusterTitle }}</div>
        </div>

        <el-row :gutter="24" style="margin-bottom: 18px;">
           <el-col :span="8">
             <el-card shadow="hover" class="dashboard-card">
               <template #header>
                 <div class="card-header">
                   <span>地址 (Bootstrap Servers)</span>
                   <el-icon><Connection /></el-icon>
                 </div>
               </template>
               <div class="metric-value metric-value-sm" style="word-break: break-all; white-space: normal; line-height: 1.4; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical;">{{ clusterInfo.bootstrapServers || '-' }}</div>
             </el-card>
           </el-col>
           <el-col :span="8">
             <el-card shadow="hover" class="dashboard-card">
               <template #header>
                 <div class="card-header">
                   <span>版本 (Version)</span>
                   <el-icon><InfoFilled /></el-icon>
                 </div>
               </template>
               <div class="metric-value">{{ clusterInfo.kafkaVersion || '-' }}</div>
             </el-card>
           </el-col>
           <el-col :span="8">
             <el-card shadow="hover" class="dashboard-card">
               <template #header>
                 <div class="card-header">
                   <span>协议 (Protocol)</span>
                   <el-icon><Lock /></el-icon>
                 </div>
               </template>
               <div class="metric-value">{{ clusterInfo.securityProtocol || 'None' }}</div>
             </el-card>
           </el-col>
        </el-row>

        <el-row :gutter="24">
          <!-- Basic Metrics Cards - Row 1 -->
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card">
              <template #header>
                <div class="card-header">
                  <span>代理数 (Brokers)</span>
                  <el-icon><Monitor /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.brokerCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card">
              <template #header>
                <div class="card-header">
                  <span>主题数 (Topics)</span>
                  <el-icon><Files /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.topicCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card">
              <template #header>
                <div class="card-header">
                  <span>分区数 (Partitions)</span>
                  <el-icon><PieChart /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.partitionCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card" :class="{ 'danger-card': metrics.underReplicatedPartitions > 0 }">
              <template #header>
                <div class="card-header">
                  <span>未同步副本 (Under Replicated)</span>
                  <el-icon><Warning /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.underReplicatedPartitions }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="24" style="margin-top: 18px;">
          <!-- Basic Metrics Cards - Row 2 -->
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card" :class="{ 'danger-card': metrics.underReplicatedReplicas > 0 }">
              <template #header>
                <div class="card-header">
                  <span>缺失副本 (Missing Replicas)</span>
                  <el-icon><Warning /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.underReplicatedReplicas }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card" :class="{ 'danger-card': metrics.offlinePartitions > 0 }">
              <template #header>
                <div class="card-header">
                  <span>离线分区 (Offline)</span>
                  <el-icon><Warning /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.offlinePartitions }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card">
              <template #header>
                <div class="card-header">
                  <span>磁盘使用 (Disk Usage)</span>
                  <el-icon><Coin /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ formatBytes(metrics.totalDiskUsageBytes) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="dashboard-card">
              <template #header>
                <div class="card-header">
                  <span>平均副本 (Avg Replicas)</span>
                  <el-icon><Monitor /></el-icon>
                </div>
              </template>
              <div class="metric-value">{{ metrics.avgReplicationFactor.toFixed(2) }}</div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
      
      <el-tab-pane label="Topics" name="topics">
        <TopicList :cluster-id="clusterId" v-if="activeTab === 'topics'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import axios from 'axios'
import TopicList from './TopicList.vue'
import { label } from '../i18n'
import { getClusters } from '../api'

const props = defineProps({
  clusterId: {
    type: Number,
    required: true
  }
})

const activeTab = ref('overview')
const loading = ref(false)
const metrics = ref({
  brokerCount: 0,
  topicCount: 0,
  partitionCount: 0,
  underReplicatedPartitions: 0,
  underReplicatedReplicas: 0,
  offlinePartitions: 0,
  avgReplicationFactor: 0,
  totalDiskUsageBytes: 0
})

const clusterInfo = ref({})
const clusterTitle = computed(() => {
  // Show bootstrap servers in title if available
  if (clusterInfo.value && clusterInfo.value.bootstrapServers) {
    return `${clusterInfo.value.bootstrapServers} - Dashboard`
  }
  return 'Cluster Dashboard'
})

let timer = null

const fetchMetrics = async () => {
  try {
    const res = await axios.get(`/api/monitor/${props.clusterId}`)
    // backend returns { code, data }
    if (res && res.data && res.data.code === 200 && res.data.data) {
      // Ensure numeric fields exist to avoid template runtime errors
      const d = res.data.data
      metrics.value = Object.assign({
        brokerCount: 0,
        topicCount: 0,
        partitionCount: 0,
        underReplicatedPartitions: 0,
        underReplicatedReplicas: 0,
        offlinePartitions: 0,
        avgReplicationFactor: 0,
        totalDiskUsageBytes: 0
      }, d)
    }
  } catch (e) {
    console.error(e)
  }
}

const fetchClusterInfo = async () => {
  try {
    const list = await getClusters()
    if (Array.isArray(list)) {
      const found = list.find(c => c.id === props.clusterId)
      if (found) clusterInfo.value = found
    }
  } catch (e) {
    console.error('Failed to fetch cluster info', e)
  }
}

const formatBytes = (bytes) => {
  if (!bytes && bytes !== 0) return 'N/A'
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// Using label(key) to show Chinese (English) format per user's preference

onMounted(() => {
  loading.value = true
  Promise.all([fetchClusterInfo(), fetchMetrics()]).finally(() => loading.value = false)
  // Auto refresh every 10 seconds
  timer = setInterval(fetchMetrics, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.header-row {
  display:flex;
  flex-direction:column;
  gap:6px;
  margin-bottom: 8px;
}
.title {
  font-weight: 700;
  font-size: 16px;
  color: #2c3e50;
}
.cluster-info {
  color: #606266;
  font-size: 13px;
  display:flex;
  gap:8px;
  align-items:center;
}
.info-item {
  white-space:nowrap;
}
.info-sep { color:#c0c4cc }

.metric-value {
  font-size: 24px;
  font-weight: bold;
  text-align: center;
  color: #409EFF;
}

.metric-value-sm {
  font-size: 16px !important;
}

.danger-card .metric-value {
  color: #F56C6C;
}

.dashboard-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
:deep(.dashboard-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
</style>
