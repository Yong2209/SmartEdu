import React, { useState, useEffect } from 'react';
import {
  BookOpen, Calendar, Shield, User, LogOut, Upload, Sparkles,
  CheckCircle2, AlertTriangle, Clock, Award, Save, Trash2, RefreshCw, FileSpreadsheet
} from 'lucide-react';
import { authApi, aiApi, timetableApi, adminApi } from './services/api';

const DAYS = ['', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];

export default function App() {
  const [user, setUser] = useState(null);
  const [activeTab, setActiveTab] = useState('ai');
  const [toast, setToast] = useState(null);

  const notify = (msg, type = 'success') => {
    setToast({ msg, type });
    setTimeout(() => setToast(null), 4000);
  };

  useEffect(() => {
    const token = localStorage.getItem('smartedu_token');
    if (token) {
      authApi.me().then((res) => setUser(res.data)).catch(() => localStorage.removeItem('smartedu_token'));
    }
  }, []);

  const handleLogout = async () => {
    try { await authApi.logout(); } catch {}
    localStorage.removeItem('smartedu_token');
    setUser(null);
    notify('Đã đăng xuất thành công');
  };

  if (!user) {
    return <AuthScreen onAuthenticated={(u) => { setUser(u); setActiveTab(u.role === 'ADMIN' ? 'admin' : 'ai'); }} notify={notify} toast={toast} />;
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      {toast && (
        <div className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-lg shadow-lg text-white font-medium ${toast.type === 'error' ? 'bg-rose-600' : 'bg-emerald-600'}`}>
          {toast.msg}
        </div>
      )}

      <header className="bg-indigo-700 text-white shadow">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <BookOpen className="w-7 h-7 text-amber-300" />
            <div>
              <h1 className="font-bold text-lg tracking-tight">SmartEdu Platform</h1>
              <p className="text-xs text-indigo-200">Học tập AI & Tối ưu Thời khóa biểu Đại học</p>
            </div>
          </div>

          <nav className="flex items-center gap-2">
            <button onClick={() => setActiveTab('ai')}
              className={`px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-1.5 ${activeTab === 'ai' ? 'bg-white text-indigo-700' : 'hover:bg-indigo-600'}`}>
              <Sparkles className="w-4 h-4" /> Học tập & Trắc nghiệm AI
            </button>
            <button onClick={() => setActiveTab('timetable')}
              className={`px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-1.5 ${activeTab === 'timetable' ? 'bg-white text-indigo-700' : 'hover:bg-indigo-600'}`}>
              <Calendar className="w-4 h-4" /> Xếp Thời khóa biểu
            </button>
            {user.role === 'ADMIN' && (
              <button onClick={() => setActiveTab('admin')}
                className={`px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-1.5 ${activeTab === 'admin' ? 'bg-white text-indigo-700' : 'hover:bg-indigo-600'}`}>
                <Shield className="w-4 h-4" /> Quản trị Hệ thống
              </button>
            )}
            <button onClick={() => setActiveTab('profile')}
              className={`px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-1.5 ${activeTab === 'profile' ? 'bg-white text-indigo-700' : 'hover:bg-indigo-600'}`}>
              <User className="w-4 h-4" /> {user.fullName}
            </button>
            <button onClick={handleLogout} className="ml-2 p-2 rounded-lg bg-indigo-800 hover:bg-rose-600 transition" title="Đăng xuất">
              <LogOut className="w-4 h-4" />
            </button>
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-6">
        {activeTab === 'ai' && <AiLearningModule notify={notify} />}
        {activeTab === 'timetable' && <TimetableModule notify={notify} />}
        {activeTab === 'admin' && user.role === 'ADMIN' && <AdminModule notify={notify} />}
        {activeTab === 'profile' && <ProfileModule user={user} setUser={setUser} notify={notify} />}
      </main>
    </div>
  );
}

function AuthScreen({ onAuthenticated, notify, toast }) {
  const [mode, setMode] = useState('login'); // login | register | otp
  const [form, setForm] = useState({ identifier: 'sinhvien@smartedu.edu.vn', password: 'SmartEdu@123', email: '', studentCode: '', fullName: '', otp: '' });
  const [devOtp, setDevOtp] = useState('');
  const [loading, setLoading] = useState(false);

  const submitLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await authApi.login({ identifier: form.identifier, password: form.password });
      localStorage.setItem('smartedu_token', res.data.accessToken);
      onAuthenticated(res.data.user);
      notify('Đăng nhập thành công!');
    } catch (err) {
      notify(err.message, 'error');
    } finally { setLoading(false); }
  };

  const submitRegister = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await authApi.register({
        email: form.email, studentCode: form.studentCode, fullName: form.fullName, password: form.password
      });
      setDevOtp(res.data.devOtpPreview || '');
      setMode('otp');
      notify('Đăng ký thành công! Nhập mã OTP 6 chữ số để kích hoạt.');
    } catch (err) {
      notify(err.message, 'error');
    } finally { setLoading(false); }
  };

  const submitOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await authApi.verifyOtp({ email: form.email, otp: form.otp });
      notify('Xác thực OTP thành công! Hãy đăng nhập.');
      setForm({ ...form, identifier: form.email });
      setMode('login');
    } catch (err) {
      notify(err.message, 'error');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-indigo-900 via-indigo-700 to-blue-600 p-4">
      {toast && (
        <div className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-lg shadow-lg text-white font-medium ${toast.type === 'error' ? 'bg-rose-600' : 'bg-emerald-600'}`}>
          {toast.msg}
        </div>
      )}
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="p-3 bg-indigo-100 text-indigo-700 rounded-xl"><BookOpen className="w-7 h-7" /></div>
          <div>
            <h2 className="text-2xl font-bold text-slate-900">SmartEdu</h2>
            <p className="text-sm text-slate-500">Nền tảng Học tập AI & Tối ưu Thời khóa biểu</p>
          </div>
        </div>

        {mode === 'login' && (
          <form onSubmit={submitLogin} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Email / Mã sinh viên / Username</label>
              <input className="w-full border rounded-lg px-3 py-2" value={form.identifier}
                onChange={(e) => setForm({ ...form, identifier: e.target.value })} required />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Mật khẩu</label>
              <input type="password" className="w-full border rounded-lg px-3 py-2" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} required />
            </div>
            <button disabled={loading} className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2.5 rounded-lg">
              {loading ? 'Đang xử lý...' : 'Đăng nhập'}
            </button>
            <div className="text-xs bg-slate-100 p-3 rounded-lg text-slate-600 space-y-1">
              <div><strong>Tài khoản SV mẫu:</strong> sinhvien@smartedu.edu.vn / SmartEdu@123</div>
              <div><strong>Tài khoản Admin mẫu:</strong> admin@smartedu.edu.vn / SmartEdu@123</div>
            </div>
            <p className="text-center text-sm text-slate-600">
              Chưa có tài khoản? <button type="button" onClick={() => setMode('register')} className="text-indigo-600 font-semibold">Đăng ký sinh viên</button>
            </p>
          </form>
        )}

        {mode === 'register' && (
          <form onSubmit={submitRegister} className="space-y-3">
            <input placeholder="Họ và tên đầy đủ" className="w-full border rounded-lg px-3 py-2"
              value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
            <input placeholder="Mã sinh viên (VD: 2021600999)" className="w-full border rounded-lg px-3 py-2"
              value={form.studentCode} onChange={(e) => setForm({ ...form, studentCode: e.target.value })} />
            <input type="email" placeholder="Email sinh viên" className="w-full border rounded-lg px-3 py-2"
              value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
            <input type="password" placeholder="Mật khẩu (>=8 ký tự, Hoa, thường, số, ký tự đặc biệt)" className="w-full border rounded-lg px-3 py-2"
              value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
            <button disabled={loading} className="w-full bg-indigo-600 text-white font-semibold py-2.5 rounded-lg">Đăng ký & Nhận OTP</button>
            <p className="text-center text-sm"><button type="button" onClick={() => setMode('login')} className="text-indigo-600">Quay lại đăng nhập</button></p>
          </form>
        )}

        {mode === 'otp' && (
          <form onSubmit={submitOtp} className="space-y-4">
            <p className="text-sm text-slate-600">Nhập mã OTP 6 chữ số đã gửi tới <strong>{form.email}</strong></p>
            {devOtp && <div className="p-2 bg-amber-50 border border-amber-200 text-amber-800 text-xs rounded">Mã OTP Demo: <strong>{devOtp}</strong></div>}
            <input placeholder="Nhập 6 số OTP" maxLength={6} className="w-full border rounded-lg px-3 py-2 text-center text-xl tracking-widest font-bold"
              value={form.otp} onChange={(e) => setForm({ ...form, otp: e.target.value })} required />
            <button disabled={loading} className="w-full bg-emerald-600 text-white font-semibold py-2.5 rounded-lg">Xác thực OTP</button>
          </form>
        )}
      </div>
    </div>
  );
}

function AiLearningModule({ notify }) {
  const [materials, setMaterials] = useState([]);
  const [questionSets, setQuestionSets] = useState([]);
  const [selectedMatId, setSelectedMatId] = useState('');
  const [qCount, setQCount] = useState(5);
  const [difficulty, setDifficulty] = useState('MIXED');
  const [activeSet, setActiveSet] = useState(null);
  const [quizSession, setQuizSession] = useState(null);
  const [answers, setAnswers] = useState({});
  const [quizResult, setQuizResult] = useState(null);
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(false);

  const loadData = async () => {
    try {
      const [mRes, sRes, hRes] = await Promise.all([aiApi.listMaterials(), aiApi.listQuestionSets(), aiApi.getQuizHistory()]);
      setMaterials(mRes.data || []);
      setQuestionSets(sRes.data || []);
      setHistory(hRes.data || null);
      if (mRes.data?.length && !selectedMatId) setSelectedMatId(mRes.data[0].id);
    } catch {}
  };

  useEffect(() => { loadData(); }, []);

  const handleUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setLoading(true);
    try {
      const res = await aiApi.uploadMaterial(file, file.name);
      notify(`Đã trích xuất xong học liệu (${res.data.pageCount} trang)`);
      await loadData();
      setSelectedMatId(res.data.id);
    } catch (err) {
      notify(err.message, 'error');
    } finally { setLoading(false); }
  };

  const handleGenerate = async () => {
    if (!selectedMatId) return notify('Vui lòng tải lên hoặc chọn học liệu trước', 'error');
    setLoading(true);
    try {
      const res = await aiApi.generateQuestions({
        learningMaterialId: Number(selectedMatId),
        questionCount: Number(qCount),
        difficulty,
        questionType: 'MULTIPLE_CHOICE'
      });
      setActiveSet(res.data);
      setQuizSession(null);
      setQuizResult(null);
      notify(`AI đã tạo xong ${res.data.questions.length} câu hỏi ôn tập!`);
      loadData();
    } catch (err) {
      notify(err.message, 'error');
    } finally { setLoading(false); }
  };

  const handleRegenerateQ = async (qId) => {
    try {
      const res = await aiApi.regenerateQuestion(qId);
      setActiveSet({
        ...activeSet,
        questions: activeSet.questions.map((q) => (q.id === qId ? res.data : q))
      });
      notify('Đã sinh lại câu hỏi mới');
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  const handleStartQuiz = async (setId) => {
    try {
      const res = await aiApi.startQuiz({ questionSetId: setId, durationMinutes: 15, shuffleQuestions: true, shuffleOptions: true });
      setQuizSession(res.data);
      setAnswers({});
      setQuizResult(null);
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  const handleSubmitQuiz = async () => {
    try {
      const payload = Object.entries(answers).map(([qtqId, optId]) => ({
        quizTestQuestionId: Number(qtqId),
        selectedOptionId: Number(optId)
      }));
      const res = await aiApi.submitQuiz(quizSession.sessionId, { answers: payload });
      setQuizResult(res.data);
      setQuizSession(null);
      notify(`Đã nộp bài! Điểm số: ${res.data.score10}/10`);
      loadData();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Upload & Cấu hình AI */}
        <div className="bg-white p-5 rounded-xl shadow-sm border space-y-4">
          <h2 className="font-bold text-slate-800 flex items-center gap-2"><Upload className="w-5 h-5 text-indigo-600" /> 1. Tải lên Học liệu (.PDF / .DOCX)</h2>
          <label className="block border-2 border-dashed border-indigo-200 rounded-xl p-4 text-center cursor-pointer hover:bg-indigo-50 transition">
            <span className="text-sm font-medium text-indigo-700">Chọn tệp tài liệu (Tối đa 20MB)</span>
            <input type="file" accept=".pdf,.docx" className="hidden" onChange={handleUpload} />
          </label>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Chọn học liệu đã trích xuất</label>
            <select className="w-full border rounded-lg px-3 py-2 text-sm" value={selectedMatId} onChange={(e) => setSelectedMatId(e.target.value)}>
              <option value="">-- Chọn học liệu --</option>
              {materials.map((m) => (
                <option key={m.id} value={m.id}>{m.title} ({m.pageCount || 1} trang)</option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Số câu (5 - 20)</label>
              <input type="number" min={5} max={20} className="w-full border rounded-lg px-3 py-2 text-sm"
                value={qCount} onChange={(e) => setQCount(e.target.value)} />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Mức độ khó</label>
              <select className="w-full border rounded-lg px-3 py-2 text-sm" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
                <option value="MIXED">Hỗn hợp</option>
                <option value="EASY">Dễ</option>
                <option value="MEDIUM">Trung bình</option>
                <option value="HARD">Khó</option>
              </select>
            </div>
          </div>

          <button disabled={loading} onClick={handleGenerate}
            className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2.5 rounded-lg flex items-center justify-center gap-2">
            <Sparkles className="w-4 h-4" /> {loading ? 'AI đang xử lý...' : 'Tạo bộ câu hỏi bằng AI'}
          </button>

          {history && (
            <div className="pt-3 border-t text-xs text-slate-600 flex justify-between">
              <span>Số bài đã làm: <strong>{history.totalAttempts}</strong></span>
              <span>Điểm TB: <strong>{history.averageScore100}/100</strong></span>
            </div>
          )}
        </div>

        {/* Khu vực hiển thị Bộ câu hỏi / Làm bài thi / Kết quả */}
        <div className="lg:col-span-2 bg-white p-6 rounded-xl shadow-sm border">
          {quizSession ? (
            <div className="space-y-5">
              <div className="flex items-center justify-between border-b pb-3">
                <h3 className="font-bold text-lg text-indigo-700">{quizSession.title}</h3>
                <span className="px-3 py-1 bg-amber-100 text-amber-800 rounded-full text-xs font-semibold flex items-center gap-1">
                  <Clock className="w-3.5 h-3.5" /> Thời gian: {quizSession.durationMinutes} phút
                </span>
              </div>
              {quizSession.questions.map((q, idx) => (
                <div key={q.quizTestQuestionId} className="p-4 rounded-lg bg-slate-50 border space-y-2">
                  <p className="font-semibold text-slate-800">Câu {idx + 1}: {q.content}</p>
                  <div className="space-y-1.5">
                    {q.options.map((o) => (
                      <label key={o.optionId} className="flex items-center gap-2 text-sm cursor-pointer p-1.5 rounded hover:bg-white">
                        <input type="radio" name={`q_${q.quizTestQuestionId}`}
                          checked={answers[q.quizTestQuestionId] === o.optionId}
                          onChange={() => setAnswers({ ...answers, [q.quizTestQuestionId]: o.optionId })} />
                        <span><strong>{o.label}.</strong> {o.content}</span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
              <button onClick={handleSubmitQuiz} className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold px-6 py-2.5 rounded-lg">
                Nộp bài & Chấm điểm
              </button>
            </div>
          ) : quizResult ? (
            <div className="space-y-4">
              <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between">
                <div>
                  <h3 className="font-bold text-emerald-900 text-lg flex items-center gap-2"><Award className="w-6 h-6" /> Kết quả: {quizResult.score10}/10 ({quizResult.score100} điểm)</h3>
                  <p className="text-sm text-emerald-700">Đúng: {quizResult.correctCount} | Sai: {quizResult.incorrectCount} | Bỏ trống: {quizResult.unansweredCount}</p>
                </div>
                <button onClick={() => setQuizResult(null)} className="px-4 py-2 bg-white border rounded-lg text-sm font-medium">Quay lại bộ câu hỏi</button>
              </div>
              {quizResult.details.map((d, idx) => (
                <div key={idx} className={`p-3 rounded-lg border text-sm ${d.isCorrect ? 'bg-emerald-50/40 border-emerald-200' : 'bg-rose-50/40 border-rose-200'}`}>
                  <p className="font-semibold">{idx + 1}. {d.questionContent}</p>
                  <p className="mt-1">Bạn chọn: <strong>{d.selectedOptionText}</strong> — Đáp án đúng: <strong className="text-emerald-700">{d.correctAnswerText}</strong></p>
                  <p className="text-xs text-slate-600 mt-1">Giải thích: {d.explanation}</p>
                </div>
              ))}
            </div>
          ) : activeSet ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
                <div>
                  <h3 className="font-bold text-lg text-slate-800">{activeSet.title}</h3>
                  <p className="text-xs text-slate-500">{activeSet.description}</p>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => aiApi.saveQuestionSet(activeSet.id, { title: activeSet.title }).then(() => notify('Đã lưu bộ câu hỏi!'))}
                    className="px-3 py-1.5 border rounded-lg text-sm font-medium flex items-center gap-1 hover:bg-slate-50">
                    <Save className="w-4 h-4" /> Lưu thư viện
                  </button>
                  <button onClick={() => handleStartQuiz(activeSet.id)}
                    className="px-4 py-1.5 bg-emerald-600 text-white rounded-lg text-sm font-semibold hover:bg-emerald-700">
                    Làm bài kiểm tra ngay
                  </button>
                </div>
              </div>

              <div className="space-y-4 max-h-[540px] overflow-y-auto pr-1">
                {activeSet.questions.map((q, idx) => (
                  <div key={q.id} className="p-4 rounded-xl border bg-slate-50/70 space-y-2">
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-semibold text-slate-800">Câu {idx + 1}: {q.content}</p>
                      <button onClick={() => handleRegenerateQ(q.id)} title="Sinh lại câu hỏi này"
                        className="text-xs px-2 py-1 bg-white border rounded hover:bg-indigo-50 text-indigo-600 flex items-center gap-1 shrink-0">
                        <RefreshCw className="w-3 h-3" /> Tạo lại
                      </button>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-1.5 text-sm">
                      {q.options.map((o) => (
                        <div key={o.id} className={`px-3 py-1.5 rounded border ${o.isCorrect ? 'bg-emerald-50 border-emerald-300 font-medium text-emerald-900' : 'bg-white'}`}>
                          <strong>{o.optionLabel}.</strong> {o.content}
                        </div>
                      ))}
                    </div>
                    <div className="text-xs text-slate-500 bg-white p-2 rounded border border-dashed">
                      <strong>Căn cứ trích dẫn từ học liệu:</strong> "{q.referenceExcerpt}"
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <h3 className="font-bold text-slate-800">Thư viện Bộ câu hỏi đã tạo ({questionSets.length})</h3>
              {questionSets.length === 0 ? (
                <p className="text-sm text-slate-500">Hãy tải lên file PDF/DOCX bên trái để AI tự động sinh câu hỏi ôn tập.</p>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {questionSets.map((qs) => (
                    <div key={qs.id} className="p-4 border rounded-xl hover:border-indigo-400 transition flex flex-col justify-between">
                      <div>
                        <h4 className="font-semibold text-slate-800">{qs.title}</h4>
                        <p className="text-xs text-slate-500 mt-1">{qs.questions.length} câu hỏi • Trạng thái: {qs.setStatus}</p>
                      </div>
                      <div className="mt-3 flex gap-2">
                        <button onClick={() => setActiveSet(qs)} className="text-xs px-3 py-1.5 border rounded-lg font-medium">Xem chi tiết</button>
                        <button onClick={() => handleStartQuiz(qs.id)} className="text-xs px-3 py-1.5 bg-indigo-600 text-white rounded-lg font-medium">Thi thử</button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function TimetableModule({ notify }) {
  const [semesters, setSemesters] = useState([]);
  const [courses, setCourses] = useState([]);
  const [selectedSem, setSelectedSem] = useState('');
  const [selectedCourses, setSelectedCourses] = useState([]);
  const [prefs, setPrefs] = useState({ minCredits: 9, maxCredits: 24, preferredShift: 'ANY', avoidSaturday: true, compactDays: true, minimizeGaps: true });
  const [genResult, setGenResult] = useState(null);
  const [activeOptionIdx, setActiveOptionIdx] = useState(0);
  const [savedList, setSavedList] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    Promise.all([timetableApi.getSemesters(), timetableApi.getCourses(), timetableApi.listSaved()])
      .then(([sRes, cRes, svRes]) => {
        setSemesters(sRes.data || []);
        setCourses(cRes.data || []);
        setSavedList(svRes.data || []);
        if (sRes.data?.length) setSelectedSem(sRes.data[0].id);
        if (cRes.data?.length) setSelectedCourses(cRes.data.slice(0, 4).map((c) => c.id));
      }).catch(() => {});
  }, []);

  const toggleCourse = (id) => {
    setSelectedCourses((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const handleGenerate = async () => {
    if (!selectedSem || selectedCourses.length === 0) return notify('Vui lòng chọn học kỳ và ít nhất 1 học phần', 'error');
    setLoading(true);
    try {
      const planRes = await timetableApi.createPlan({ semesterId: Number(selectedSem), courseIds: selectedCourses });
      const planId = planRes.data.planId;
      await timetableApi.savePreferences(planId, prefs);
      const genRes = await timetableApi.generateSchedules(planId);
      setGenResult(genRes.data);
      setActiveOptionIdx(0);
      notify(genRes.data.message);
    } catch (err) {
      notify(err.message, 'error');
    } finally { setLoading(false); }
  };

  const handleSaveFavorite = async (opt) => {
    try {
      await timetableApi.saveFavorite({ scheduleOptionId: opt.id, displayName: opt.optionName });
      const sv = await timetableApi.listSaved();
      setSavedList(sv.data || []);
      notify('Đã lưu thời khóa biểu vào danh sách yêu thích!');
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  const currentOpt = genResult?.options?.[activeOptionIdx];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Cột cấu hình học phần & ràng buộc */}
        <div className="bg-white p-5 rounded-xl shadow-sm border space-y-4">
          <h2 className="font-bold text-slate-800 flex items-center gap-2"><Calendar className="w-5 h-5 text-indigo-600" /> 1. Chọn Học phần & Tiêu chí</h2>
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">Học kỳ áp dụng</label>
            <select className="w-full border rounded-lg px-3 py-2 text-sm" value={selectedSem} onChange={(e) => setSelectedSem(e.target.value)}>
              {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterName} ({s.semesterCode})</option>)}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">Danh sách học phần dự kiến ({selectedCourses.length} môn)</label>
            <div className="max-h-44 overflow-y-auto border rounded-lg divide-y">
              {courses.map((c) => (
                <label key={c.id} className="flex items-center justify-between px-3 py-2 text-sm hover:bg-slate-50 cursor-pointer">
                  <div className="flex items-center gap-2">
                    <input type="checkbox" checked={selectedCourses.includes(c.id)} onChange={() => toggleCourse(c.id)} />
                    <span><strong>{c.courseCode}</strong> - {c.courseName}</span>
                  </div>
                  <span className="text-xs text-slate-500">{c.credits} TC</span>
                </label>
              ))}
            </div>
          </div>

          <div className="space-y-2 pt-2 border-t text-sm">
            <label className="block text-xs font-semibold text-slate-600">Buổi học ưu tiên</label>
            <select className="w-full border rounded-lg px-3 py-1.5" value={prefs.preferredShift}
              onChange={(e) => setPrefs({ ...prefs, preferredShift: e.target.value })}>
              <option value="ANY">Linh hoạt cả ngày</option>
              <option value="MORNING">Ưu tiên Buổi Sáng (Tiết 1-6)</option>
              <option value="AFTERNOON">Ưu tiên Buổi Chiều (Tiết 7-12)</option>
            </select>
            <label className="flex items-center gap-2"><input type="checkbox" checked={prefs.avoidSaturday}
              onChange={(e) => setPrefs({ ...prefs, avoidSaturday: e.target.checked })} /> Tránh học Thứ 7</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={prefs.compactDays}
              onChange={(e) => setPrefs({ ...prefs, compactDays: e.target.checked })} /> Gom ngày học trong tuần</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={prefs.minimizeGaps}
              onChange={(e) => setPrefs({ ...prefs, minimizeGaps: e.target.checked })} /> Giảm tiết trống giữa giờ</label>
          </div>

          <button disabled={loading} onClick={handleGenerate}
            className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2.5 rounded-lg">
            {loading ? 'Đang chạy Backtracking...' : 'Sinh Thời khóa biểu Tối ưu'}
          </button>
        </div>

        {/* Cột hiển thị kết quả Lưới Thời khóa biểu */}
        <div className="lg:col-span-2 bg-white p-6 rounded-xl shadow-sm border space-y-4">
          {genResult && genResult.options?.length > 0 ? (
            <>
              <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
                <div className="flex flex-wrap gap-1.5">
                  {genResult.options.map((opt, i) => (
                    <button key={opt.id} onClick={() => setActiveOptionIdx(i)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold border ${i === activeOptionIdx ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-50 text-slate-700'}`}>
                      PA #{opt.optionRank} ({opt.totalScore}đ)
                    </button>
                  ))}
                </div>
                {currentOpt && (
                  <button onClick={() => handleSaveFavorite(currentOpt)}
                    className="px-3 py-1.5 bg-emerald-600 text-white rounded-lg text-xs font-semibold flex items-center gap-1">
                    <Save className="w-3.5 h-3.5" /> Lưu TKB này
                  </button>
                )}
              </div>

              {currentOpt && (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-center justify-between bg-indigo-50 p-3 rounded-lg text-sm">
                    <span className="font-bold text-indigo-900">{currentOpt.optionName}</span>
                    <span className="font-semibold text-indigo-700">Tổng tín chỉ: {currentOpt.totalCredits} TC</span>
                  </div>

                  {currentOpt.warnings?.length > 0 && (
                    <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg space-y-1">
                      {currentOpt.warnings.map((w, idx) => (
                        <div key={idx} className="text-xs text-amber-800 flex items-center gap-1.5">
                          <AlertTriangle className="w-4 h-4 shrink-0" /> {w.warningMessage}
                        </div>
                      ))}
                    </div>
                  )}

                  <div className="overflow-x-auto border rounded-lg">
                    <table className="w-full text-sm text-left">
                      <thead className="bg-slate-100 text-slate-700 text-xs uppercase">
                        <tr>
                          <th className="px-3 py-2">Mã lớp</th>
                          <th className="px-3 py-2">Tên học phần</th>
                          <th className="px-3 py-2">TC</th>
                          <th className="px-3 py-2">Lịch học</th>
                          <th className="px-3 py-2">Phòng / Cơ sở</th>
                          <th className="px-3 py-2">Giảng viên</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y">
                        {currentOpt.sections.map((sec) => (
                          <tr key={sec.id} className="hover:bg-slate-50">
                            <td className="px-3 py-2 font-semibold text-indigo-700">{sec.sectionCode}</td>
                            <td className="px-3 py-2">{sec.courseName}</td>
                            <td className="px-3 py-2">{sec.credits}</td>
                            <td className="px-3 py-2">
                              {sec.meetings.map((m, i) => (
                                <span key={i} className="inline-block bg-indigo-50 text-indigo-800 text-xs px-2 py-0.5 rounded mr-1">
                                  {DAYS[m.dayOfWeek]} (Tiết {m.startPeriod}-{m.endPeriod})
                                </span>
                              ))}
                            </td>
                            <td className="px-3 py-2 text-xs">{sec.meetings.map((m) => `${m.roomCode} (${m.campusCode})`).join(', ')}</td>
                            <td className="px-3 py-2 text-xs text-slate-600">{sec.lecturerName}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                    {currentOpt.evaluations.map((ev) => (
                      <div key={ev.criterionCode} className="p-2.5 border rounded-lg bg-slate-50 text-xs">
                        <div className="font-semibold text-slate-700">{ev.criterionName}</div>
                        <div className="text-indigo-700 font-bold text-base mt-0.5">{ev.scoreValue}/100</div>
                        <div className="text-slate-500 mt-0.5">{ev.explanation}</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="space-y-4">
              <h3 className="font-bold text-slate-800">Thời khóa biểu đã lưu của bạn ({savedList.length}/5)</h3>
              {savedList.length === 0 ? (
                <p className="text-sm text-slate-500">Chọn danh sách học phần bên trái và nhấn "Sinh Thời khóa biểu Tối ưu" để hệ thống tính toán các phương án không trùng tiết.</p>
              ) : (
                <div className="space-y-3">
                  {savedList.map((sv) => (
                    <div key={sv.id} className="p-4 border rounded-xl flex items-center justify-between">
                      <div>
                        <h4 className="font-semibold text-slate-800">{sv.displayName}</h4>
                        <p className="text-xs text-slate-500">{sv.option.sections.length} học phần • {sv.option.totalCredits} tín chỉ • Điểm: {sv.option.totalScore}/100</p>
                      </div>
                      <button onClick={() => timetableApi.deleteSaved(sv.id).then(() => setSavedList(savedList.filter((x) => x.id !== sv.id)))}
                        className="p-2 text-rose-600 hover:bg-rose-50 rounded-lg"><Trash2 className="w-4 h-4" /></button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function AdminModule({ notify }) {
  const [users, setUsers] = useState([]);
  const [logs, setLogs] = useState(null);
  const [semesters, setSemesters] = useState([]);
  const [semId, setSemId] = useState('');

  const loadAdmin = async () => {
    try {
      const [uRes, lRes, sRes] = await Promise.all([adminApi.listUsers({}), adminApi.getLogs(), timetableApi.getSemesters()]);
      setUsers(uRes.data.content || []);
      setLogs(lRes.data);
      setSemesters(sRes.data || []);
      if (sRes.data?.length) setSemId(sRes.data[0].id);
    } catch {}
  };

  useEffect(() => { loadAdmin(); }, []);

  const handleImportCatalog = async (e) => {
    const file = e.target.files?.[0];
    if (!file || !semId) return;
    try {
      const res = await adminApi.importCatalog(semId, file);
      notify(`Import thành công ${res.data.successRows}/${res.data.totalRows} lớp học phần!`);
      loadAdmin();
    } catch (err) { notify(err.message, 'error'); }
  };

  const toggleLock = async (u) => {
    const next = u.accountStatus === 'LOCKED' ? 'ACTIVE' : 'LOCKED';
    try {
      await adminApi.updateUserStatus(u.id, next, 'Cập nhật bởi quản trị viên');
      notify(`Đã chuyển trạng thái ${u.email} sang ${next}`);
      loadAdmin();
    } catch (err) { notify(err.message, 'error'); }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded-xl border shadow-sm">
          <div className="text-xs text-slate-500">Tổng người dùng</div>
          <div className="text-2xl font-bold text-indigo-700">{logs?.summary?.totalUsers || 0}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border shadow-sm">
          <div className="text-xs text-slate-500">Tổng học phần danh mục</div>
          <div className="text-2xl font-bold text-emerald-700">{logs?.summary?.totalCourses || 0}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border shadow-sm">
          <div className="text-xs text-slate-500">Tổng lớp học phần mở</div>
          <div className="text-2xl font-bold text-amber-600">{logs?.summary?.totalSections || 0}</div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-xl border shadow-sm flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <FileSpreadsheet className="w-6 h-6 text-emerald-600" />
          <div>
            <h3 className="font-bold text-slate-800">Import Danh mục Học phần & Lớp mở (.XLSX)</h3>
            <p className="text-xs text-slate-500">Cột: Mã môn | Tên môn | Tín chỉ | Mã lớp | Giảng viên | Thứ (1-7) | Tiết BĐ | Tiết KT | Phòng | Sĩ số</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <select className="border rounded-lg px-3 py-2 text-sm" value={semId} onChange={(e) => setSemId(e.target.value)}>
            {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterCode}</option>)}
          </select>
          <label className="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg text-sm font-semibold cursor-pointer">
            Tải lên Excel
            <input type="file" accept=".xlsx" className="hidden" onChange={handleImportCatalog} />
          </label>
        </div>
      </div>

      <div className="bg-white p-5 rounded-xl border shadow-sm">
        <h3 className="font-bold text-slate-800 mb-3">Quản lý Tài khoản Người dùng</h3>
        <table className="w-full text-sm text-left">
          <thead className="bg-slate-100 text-xs uppercase">
            <tr>
              <th className="px-3 py-2">Họ tên</th>
              <th className="px-3 py-2">Email</th>
              <th className="px-3 py-2">Mã SV</th>
              <th className="px-3 py-2">Vai trò</th>
              <th className="px-3 py-2">Trạng thái</th>
              <th className="px-3 py-2">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {users.map((u) => (
              <tr key={u.id}>
                <td className="px-3 py-2 font-medium">{u.fullName}</td>
                <td className="px-3 py-2">{u.email}</td>
                <td className="px-3 py-2">{u.studentCode || '-'}</td>
                <td className="px-3 py-2"><span className="px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 text-xs font-semibold">{u.role}</span></td>
                <td className="px-3 py-2">
                  <span className={`px-2 py-0.5 rounded text-xs font-semibold ${u.accountStatus === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}>
                    {u.accountStatus}
                  </span>
                </td>
                <td className="px-3 py-2">
                  <button onClick={() => toggleLock(u)} className="text-xs px-2.5 py-1 border rounded hover:bg-slate-50">
                    {u.accountStatus === 'LOCKED' ? 'Mở khóa' : 'Khóa TK'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ProfileModule({ user, setUser, notify }) {
  const [form, setForm] = useState({ fullName: user.fullName || '', phone: user.phone || '', address: user.address || '', gender: user.gender || '' });

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      const res = await authApi.updateProfile(form);
      setUser(res.data);
      notify('Cập nhật hồ sơ cá nhân thành công!');
    } catch (err) { notify(err.message, 'error'); }
  };

  return (
    <div className="max-w-xl mx-auto bg-white p-6 rounded-xl border shadow-sm space-y-4">
      <h2 className="font-bold text-lg text-slate-800 flex items-center gap-2"><CheckCircle2 className="w-5 h-5 text-emerald-600" /> Hồ sơ Cá nhân</h2>
      <div className="grid grid-cols-2 gap-3 text-sm bg-slate-50 p-3 rounded-lg">
        <div><span className="text-slate-500">Email:</span> <strong>{user.email}</strong></div>
        <div><span className="text-slate-500">Mã sinh viên:</span> <strong>{user.studentCode || 'N/A'}</strong></div>
      </div>
      <form onSubmit={handleSave} className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Họ và tên</label>
          <input className="w-full border rounded-lg px-3 py-2 text-sm" value={form.fullName}
            onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Số điện thoại</label>
            <input className="w-full border rounded-lg px-3 py-2 text-sm" value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Giới tính</label>
            <select className="w-full border rounded-lg px-3 py-2 text-sm" value={form.gender}
              onChange={(e) => setForm({ ...form, gender: e.target.value })}>
              <option value="">-- Chọn --</option>
              <option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option>
            </select>
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Địa chỉ</label>
          <input className="w-full border rounded-lg px-3 py-2 text-sm" value={form.address}
            onChange={(e) => setForm({ ...form, address: e.target.value })} />
        </div>
        <button className="bg-indigo-600 text-white font-semibold px-5 py-2 rounded-lg text-sm">Lưu thay đổi</button>
      </form>
    </div>
  );
}
