/**
 * Content Management JS - Eduverse v5
 * Handles Courses, Lessons, Video Upload & HLS Playback
 */

let currentVideoMode = 'youtube';
let selectedVideoFile = null;

// ── Video Mode Selector ──
window.setVideoMode = function(mode) {
    currentVideoMode = mode;
    const ytSection = document.getElementById('youtubeModeSection');
    const upSection = document.getElementById('uploadModeSection');
    const btnYt = document.getElementById('selectYoutube');
    const btnUp = document.getElementById('selectUpload');

    if (mode === 'youtube') {
        ytSection.style.display = 'block';
        upSection.style.display = 'none';
        btnYt.className = 'btn btn-primary btn-sm';
        btnUp.className = 'btn btn-outline btn-sm';
    } else {
        ytSection.style.display = 'none';
        upSection.style.display = 'block';
        btnUp.className = 'btn btn-primary btn-sm';
        btnYt.className = 'btn btn-outline btn-sm';
    }
};

// ── Load Courses ──
async function loadCourses() {
    const list = document.getElementById('courseList');
    if (!list) return;

    try {
        const response = await EduverseAuth.apiFetch('/courses');
        const courses = await response.json();
        const isStudent = localStorage.getItem('userRole') === 'STUDENT';

        if (courses.length === 0) {
            list.innerHTML = `<div style="grid-column: 1/-1; text-align: center; padding: 4rem;">
                <p style="color: var(--gray-500); font-size: 1.1rem;">لم يتم العثور على دورات تعليمية. ابدأ بإنشاء دورتك الأولى!</p>
            </div>`;
            return;
        }

        list.innerHTML = courses.map(c => `
            <div class="course-card">
                <img src="${c.thumbnailUrl || 'https://images.unsplash.com/photo-1501504905252-473c47e087f8?auto=format&fit=crop&q=80&w=400'}" class="thumb" alt="${c.title}">
                <div class="card-body">
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.25rem;">
                        <h3>${c.title}</h3>
                        <span class="badge ${c.published ? 'badge-success' : 'badge-warning'}">${c.published ? 'منشور' : 'مسودة'}</span>
                    </div>
                    <p>${c.description || 'لا يوجد وصف لهذه الدورة.'}</p>
                </div>
                <div class="card-footer">
                    <a href="/course-details.html?id=${c.id}" class="btn btn-primary btn-sm" style="flex: 1;">
                        ${isStudent ? 'عرض الدروس' : 'إدارة المحتوى'}
                    </a>
                    <button onclick="deleteCourse(${c.id})" class="btn btn-sm admin-only" style="background: var(--danger-light); color: var(--danger);">🗑️</button>
                </div>
            </div>
        `).join('');
    } catch (err) {
        console.error('Error loading courses:', err);
    }
}

async function createCourse(data) {
    try {
        const response = await EduverseAuth.apiFetch('/courses', { method: 'POST', body: JSON.stringify(data) });
        if (response.ok) location.reload();
    } catch (err) { alert('فشل إنشاء الدورة'); }
}

// ── Load Course Details & Lessons ──
async function loadCourseDetails() {
    const courseId = new URLSearchParams(window.location.search).get('id');
    if (!courseId || !document.getElementById('lessonList')) return;

    try {
        const courseRes = await EduverseAuth.apiFetch(`/courses/${courseId}`);
        const course = await courseRes.json();
        document.getElementById('courseTitle').textContent = course.title;
        document.getElementById('courseDesc').textContent = course.description || '';

        const lessonRes = await EduverseAuth.apiFetch(`/courses/${courseId}/lessons`);
        const lessons = await lessonRes.json();
        const lessonList = document.getElementById('lessonList');

        if (lessons.length === 0) {
            lessonList.innerHTML = '<div class="no-video-banner"><p style="font-size: 1.1rem;">لا توجد دروس حالياً. أضف الدرس الأول من الأعلى.</p></div>';
            return;
        }

        const tenantId = localStorage.getItem('tenantId');

        lessonList.innerHTML = lessons.map(lesson => {
            const videoId = getYouTubeId(lesson.youtubeUrl);
            const isHosted = lesson.videoType === 'SELF_HOSTED';
            const isReady = lesson.videoStatus === 'READY';
            const isProcessing = lesson.videoStatus === 'PROCESSING';

            let videoHTML = '';
            if (isHosted && isReady) {
                videoHTML = `<div class="video-wrapper">
                    <video id="hlsPlayer_${lesson.id}" controls
                        controlsList="nodownload noremoteplayback"
                        oncontextmenu="return false"
                        data-src="/api/videos/${tenantId}/${lesson.id}/master.m3u8"></video>
                </div>`;
            } else if (isHosted && isProcessing) {
                videoHTML = `<div class="processing-banner">
                    <div class="spinner"></div>
                    <h4 style="color: #9a7b13; font-weight: 700; margin-bottom: 0.35rem;">⏳ جاري معالجة الفيديو...</h4>
                    <p style="color: #9a7b13; font-size: 0.85rem;">قد يستغرق هذا بضع دقائق. سيتم تحديث الصفحة تلقائياً.</p>
                </div>`;
            } else if (videoId) {
                videoHTML = `<div class="video-wrapper video-wrapper-yt">
                    <iframe src="https://www.youtube.com/embed/${videoId}?rel=0&modestbranding=1&showinfo=0&iv_load_policy=3"
                        title="YouTube video" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen></iframe>
                </div>`;
            } else {
                videoHTML = `<div class="no-video-banner"><p>❌ لا يوجد فيديو مرتبط بهذا الدرس.</p></div>`;
            }

            return `
                <div class="lesson-item">
                    <div class="lesson-header">
                        <div class="lesson-title">
                            <div class="lesson-number">${lesson.orderIndex}</div>
                            <h4>${lesson.title}</h4>
                        </div>
                        <button onclick="deleteLesson(${lesson.id})" class="btn btn-sm admin-only" style="background: var(--danger-light); color: var(--danger);">حذف 🗑️</button>
                    </div>
                    ${videoHTML}
                </div>`;
        }).join('');

        // Init HLS players
        lessons.forEach(lesson => {
            if (lesson.videoType === 'SELF_HOSTED' && lesson.videoStatus === 'READY') {
                initHLSPlayer(`hlsPlayer_${lesson.id}`);
            }
        });

        // Auto-refresh if processing
        if (lessons.some(l => l.videoStatus === 'PROCESSING')) {
            setTimeout(() => location.reload(), 15000);
        }

        // Content protection
        document.body.style.userSelect = 'none';
        document.body.oncontextmenu = (e) => e.preventDefault();

    } catch (err) {
        console.error('Error loading course details:', err);
    }
}

// ── HLS Player ──
function initHLSPlayer(id) {
    const video = document.getElementById(id);
    if (!video) return;
    const src = video.getAttribute('data-src');

    if (Hls.isSupported()) {
        const hls = new Hls({ maxLoadingDelay: 4, maxBufferLength: 30, maxMaxBufferLength: 60 });
        hls.loadSource(src);
        hls.attachMedia(video);
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = src;
    }
}

function getYouTubeId(url) {
    if (!url) return null;
    const match = url.match(/^.*(youtu.be\/|v\/|u\/\w\/|embed\/|watch\?v=|\&v=)([^#\&\?]*).*/);
    return (match && match[2].length === 11) ? match[2] : null;
}

// ── Upload Video ──
async function uploadVideoFile(lessonId, file) {
    const progressDiv = document.getElementById('uploadProgress');
    const progressBar = document.getElementById('uploadProgressBar');
    const percentText = document.getElementById('uploadPercent');
    const statusText = document.getElementById('uploadStatusText');

    progressDiv.style.display = 'block';
    statusText.textContent = '🚀 جاري رفع الفيديو...';

    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        const fd = new FormData();
        fd.append('file', file);

        xhr.upload.onprogress = (e) => {
            if (e.lengthComputable) {
                const pct = Math.round((e.loaded / e.total) * 100);
                progressBar.style.width = pct + '%';
                percentText.textContent = 'تم رفع ' + pct + '%';
            }
        };

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                statusText.textContent = '✅ اكتمل الرفع! جاري المعالجة...';
                progressBar.style.width = '100%';
                percentText.textContent = '100% — جاري تحويل الجودة...';
                resolve(JSON.parse(xhr.responseText));
            } else {
                statusText.textContent = '❌ فشل الرفع.';
                reject(new Error('Upload failed'));
            }
        };

        xhr.onerror = () => {
            statusText.textContent = '❌ خطأ في الاتصال.';
            reject(new Error('Connection error'));
        };

        xhr.open('POST', `/api/lessons/${lessonId}/video`);
        xhr.setRequestHeader('Authorization', 'Bearer ' + EduverseAuth.getAccessToken());
        xhr.setRequestHeader('X-TenantID', localStorage.getItem('tenantId') || 'public');
        xhr.send(fd);
    });
}

// ── UI Event Handlers ──
document.addEventListener('DOMContentLoaded', () => {
    const courseModal = document.getElementById('courseModal');
    const lessonModal = document.getElementById('lessonModal');

    // Courses Page
    if (document.getElementById('showAddCourse')) {
        document.getElementById('showAddCourse').onclick = () => courseModal.classList.add('show');
        document.getElementById('closeCourseModal').onclick = () => courseModal.classList.remove('show');
        document.getElementById('courseForm').onsubmit = (e) => {
            e.preventDefault();
            createCourse(Object.fromEntries(new FormData(e.target)));
        };
        loadCourses();
    }

    // Dropzone
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('videoFileInput');
    const fileNameDisplay = document.getElementById('selectedFileName');

    if (dropzone) {
        dropzone.onclick = () => fileInput.click();
        dropzone.ondragover = (e) => { e.preventDefault(); dropzone.classList.add('dragover'); };
        dropzone.ondragleave = () => dropzone.classList.remove('dragover');
        dropzone.ondrop = (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                selectedVideoFile = e.dataTransfer.files[0];
                fileNameDisplay.style.display = 'block';
                fileNameDisplay.textContent = '✅ ' + selectedVideoFile.name + ' (' + (selectedVideoFile.size / 1024 / 1024).toFixed(1) + ' MB)';
            }
        };
    }

    if (fileInput) {
        fileInput.onchange = () => {
            if (fileInput.files.length > 0) {
                selectedVideoFile = fileInput.files[0];
                fileNameDisplay.style.display = 'block';
                fileNameDisplay.textContent = '✅ ' + selectedVideoFile.name + ' (' + (selectedVideoFile.size / 1024 / 1024).toFixed(1) + ' MB)';
            }
        };
    }

    // Lesson Form
    if (document.getElementById('showAddLesson')) {
        const courseId = new URLSearchParams(window.location.search).get('id');
        document.getElementById('showAddLesson').onclick = () => lessonModal.classList.add('show');
        document.getElementById('closeLessonModal').onclick = () => lessonModal.classList.remove('show');

        document.getElementById('lessonForm').onsubmit = async (e) => {
            e.preventDefault();
            const data = Object.fromEntries(new FormData(e.target));
            if (currentVideoMode === 'upload') delete data.youtubeUrl;

            const response = await EduverseAuth.apiFetch(`/courses/${courseId}/lessons`, {
                method: 'POST', body: JSON.stringify(data)
            });

            if (response.ok) {
                const lesson = await response.json();
                if (currentVideoMode === 'upload' && selectedVideoFile) {
                    try {
                        await uploadVideoFile(lesson.id, selectedVideoFile);
                        setTimeout(() => location.reload(), 3000);
                    } catch (err) {
                        alert('فشل رفع الفيديو: ' + err.message);
                        location.reload();
                    }
                } else {
                    location.reload();
                }
            } else {
                alert('فشل إنشاء الدرس: ' + await response.text());
            }
        };

        loadCourseDetails();
    }
});

// ── Global Actions ──
window.deleteCourse = async (id) => {
    if (confirm('هل أنت متأكد من حذف هذه الدورة؟ سيتم حذف جميع الدروس.')) {
        await EduverseAuth.apiFetch(`/courses/${id}`, { method: 'DELETE' });
        location.reload();
    }
};

window.deleteLesson = async (id) => {
    if (confirm('هل أنت متأكد من حذف هذا الدرس؟')) {
        await EduverseAuth.apiFetch(`/lessons/${id}`, { method: 'DELETE' });
        location.reload();
    }
};
