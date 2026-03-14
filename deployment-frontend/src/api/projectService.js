import api from './axios'

export const projectService = {
  getAll: () => api.get('/projects'),
  getById: (id) => api.get(`/projects/${id}`),
  create: (data) => api.post('/projects', data),
  update: (id, data) => api.put(`/projects/${id}`, data),
  delete: (id) => api.delete(`/projects/${id}`),
}

export const deploymentService = {
  deploy: (projectId) => api.post(`/projects/${projectId}/deployments`),
  getLatest: (projectId) => api.get(`/projects/${projectId}/deployments/latest`),
  getAll: (projectId) => api.get(`/projects/${projectId}/deployments`),
  getLogs: (projectId, deploymentId) => api.get(`/projects/${projectId}/deployments/${deploymentId}/logs`),
  stop: (projectId) => api.post(`/projects/${projectId}/deployments/stop`),
  restart: (projectId) => api.post(`/projects/${projectId}/deployments/restart`),
  redeploy: (projectId) => api.post(`/projects/${projectId}/deployments/redeploy`),
}