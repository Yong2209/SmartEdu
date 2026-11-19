import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('smartedu_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.message || err.message || 'Lỗi kết nối máy chủ';
    return Promise.reject(new Error(msg));
  }
);

export const authApi = {
  register: (data) => api.post('/auth/register', data),
  verifyOtp: (data) => api.post('/auth/verify-otp', data),
  login: (data) => api.post('/auth/login', data),
  logout: () => api.post('/auth/logout'),
  me: () => api.get('/auth/me'),
  updateProfile: (data) => api.put('/auth/me', data),
  changePassword: (data) => api.post('/auth/change-password', data),
  forgotPassword: (data) => api.post('/auth/forgot-password', data),
  resetPassword: (data) => api.post('/auth/reset-password', data),
};

export const aiApi = {
  uploadMaterial: (file, title) => {
    const fd = new FormData();
    fd.append('file', file);
    if (title) fd.append('title', title);
    return api.post('/ai/materials/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  listMaterials: () => api.get('/ai/materials'),
  getMaterialContent: (id) => api.get(`/ai/materials/${id}/content`),
  generateQuestions: (data) => api.post('/ai/questions/generate', data),
  updateQuestion: (id, data) => api.put(`/ai/questions/${id}`, data),
  regenerateQuestion: (id) => api.post(`/ai/questions/${id}/regenerate`),
  saveQuestionSet: (setId, data) => api.post(`/ai/question-sets/${setId}/save`, data),
  listQuestionSets: () => api.get('/ai/question-sets'),
  startQuiz: (data) => api.post('/ai/quizzes/start', data),
  submitQuiz: (attemptId, data) => api.post(`/ai/quizzes/attempts/${attemptId}/submit`, data),
  reviewWrong: (attemptId) => api.post(`/ai/quizzes/attempts/${attemptId}/review-wrong`),
  getQuizHistory: () => api.get('/ai/quizzes/history'),
};

export const timetableApi = {
  getSemesters: () => api.get('/timetable/semesters'),
  getCourses: () => api.get('/timetable/courses'),
  getSections: (semesterId, courseIds) =>
    api.get('/timetable/sections', { params: { semesterId, courseIds: courseIds?.join(',') } }),
  importCourses: (semesterId, file) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post(`/timetable/import-courses?semesterId=${semesterId}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  createPlan: (data) => api.post('/timetable/plans', data),
  savePreferences: (planId, data) => api.put(`/timetable/plans/${planId}/preferences`, data),
  generateSchedules: (planId) => api.post(`/timetable/plans/${planId}/generate`),
  manualEditOption: (optionId, data) => api.post(`/timetable/options/${optionId}/manual-edit`, data),
  saveFavorite: (data) => api.post('/timetable/saved', data),
  listSaved: () => api.get('/timetable/saved'),
  deleteSaved: (id) => api.delete(`/timetable/saved/${id}`),
  exportOption: (optionId, format) => api.post(`/timetable/options/${optionId}/export?format=${format}`),
};

export const adminApi = {
  importCatalog: (semesterId, file) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post(`/admin/catalog/import?semesterId=${semesterId}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  listUsers: (params) => api.get('/admin/users', { params }),
  updateUserStatus: (id, status, reason) => api.patch(`/admin/users/${id}/status`, null, { params: { status, reason } }),
  updateUserRole: (id, roleCode) => api.patch(`/admin/users/${id}/role`, null, { params: { roleCode } }),
  getLogs: () => api.get('/admin/monitoring/logs'),
};
