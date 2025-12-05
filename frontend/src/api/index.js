import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  maxRedirects: 0, // Don't follow redirects
  validateStatus: function (status) {
    return status >= 200 && status < 400 // Accept 2xx and 3xx status codes for redirects
  }
})

// Log outgoing requests to help debug dev proxy issues
api.interceptors.request.use(config => {
  try {
    // Log method and full URL that axios will request (relative base + url)
    const base = config.baseURL || ''
    const url = (base.endsWith('/') || (config.url && config.url.startsWith('/'))) ? `${base}${config.url}` : `${base}/${config.url}`
    // Keep logs minimal but informative
    // eslint-disable-next-line no-console
    console.debug('[API Request]', config.method?.toUpperCase(), url, 'params=', config.params || '', 'data=', config.data || '')
  } catch (err) {
    // eslint-disable-next-line no-console
    console.debug('[API Request] could not format log', err)
  }
  return config
}, error => {
  return Promise.reject(error)
})

api.interceptors.response.use(
  response => {
    // Handle redirects (302) - redirect to login
    if (response.status === 302 && response.headers.location && response.headers.location.includes('/login')) {
      // Clear login state and redirect to login page
      sessionStorage.removeItem('loggedIn')
      // Redirect to login page (using relative path to adapt to any port/domain)
      window.location.href = '/login'
      return Promise.reject(new Error('Unauthorized'))
    }

    const res = response.data
    if (res.code === 200) {
      return res.data
    } else {
      ElMessage.error(res.message || 'Error')
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  error => {
    // Handle network errors or other axios errors
    if (error.response) {
      if (error.response.status === 302) {
        // Handle redirect to login
        sessionStorage.removeItem('loggedIn')
        // Redirect to login page (using relative path to adapt to any port/domain)
        window.location.href = '/login'
        return Promise.reject(new Error('Unauthorized'))
      } else if (error.response.status === 401 || error.response.status === 403) {
        // Handle authentication errors
        sessionStorage.removeItem('loggedIn')
        // Redirect to login page (using relative path to adapt to any port/domain)
        window.location.href = '/login'
        return Promise.reject(new Error('Authentication required'))
      }
    }

    ElMessage.error(error.message || 'Network Error')
    return Promise.reject(error)
  }
)

export const getClusters = () => api.get('/clusters')
export const addCluster = (data) => api.post('/clusters', data)
export const updateCluster = (data) => api.put('/clusters', data)
export const deleteCluster = (id) => api.delete(`/clusters/${id}`)

export const getTopics = (clusterId, page = 1, pageSize = 10, keyword = '') => 
  api.get(`/clusters/${clusterId}/topics`, { params: { page, pageSize, keyword } })
export const createTopic = (clusterId, data) => api.post(`/clusters/${clusterId}/topics`, data)
export const deleteTopic = (clusterId, topicName) => api.delete(`/clusters/${clusterId}/topics/${topicName}`)
export const getTopicPartitions = (clusterId, topicName, page = 1, pageSize = 10) => 
  api.get(`/clusters/${clusterId}/topics/${topicName}/partitions`, { params: { page, pageSize } })
export const getTopicConfigs = (clusterId, topicName) => api.get(`/clusters/${clusterId}/topics/${topicName}/configs`)
export const getTopicProducers = (clusterId, topicName, page = 1, pageSize = 10) => 
  api.get(`/clusters/${clusterId}/topics/${topicName}/producers`, { params: { page, pageSize } })
export const getTopicMessages = (clusterId, topicName, params) => 
  api.get(`/clusters/${clusterId}/topics/${topicName}/messages`, { 
    params,
    paramsSerializer: {
      indexes: null // Use brackets for arrays: partitions[]=1&partitions[]=2
    }
  })
export const sendTopicMessage = (clusterId, topicName, data) => api.post(`/clusters/${clusterId}/topics/${topicName}/messages`, data)
export const getMessageHistory = (clusterId, topicName, page = 1, pageSize = 10) => 
  api.get(`/clusters/${clusterId}/topics/${topicName}/messages/history`, { params: { page, pageSize } })
export const updateTopicConfigs = (clusterId, topicName, configs) => api.put(`/clusters/${clusterId}/topics/${topicName}/configs`, configs)

export const getConsumerGroups = (clusterId, page = 1, pageSize = 10, keyword = '', topic = '') =>
  api.get(`/clusters/${clusterId}/consumer-groups`, { params: { page, pageSize, keyword, topic } })

// Get topic volume (message counts) for the last N days (frontend will request days=7)
export const getTopicVolume = (clusterId, topicName, days = 7) =>
  api.get(`/clusters/${clusterId}/topics/${topicName}/volume`, { params: { days } })
export const getTopicsVolume = (clusterId, topicNames, days = 7) =>
  api.get(`/clusters/${clusterId}/topics/volumes`, { params: { topics: topicNames, days }, paramsSerializer: { indexes: null } })

// Trigger backfill for specific topics (POST) - topics can be repeated: ?topics=a&topics=b
export const postBackfillTopics = (clusterId, topicNames = [], days = 7, all = false) =>
  api.post(`/clusters/${clusterId}/topics/volumes/backfill`, null, { params: { topics: topicNames, days, all }, paramsSerializer: { indexes: null } })

// Login API
export const login = (username, password) => {
  // In both dev and prod, we use relative path so the request goes to the same origin
  // (Vite dev server in dev, or the backend itself in prod).
  // This ensures cookies (JSESSIONID) are set on the correct domain.
  return axios.post('/login', new URLSearchParams({
    username: username,
    password: password
  }), {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    maxRedirects: 0,
    validateStatus: function (status) {
      return status >= 200 && status < 400
    }
  })
}
