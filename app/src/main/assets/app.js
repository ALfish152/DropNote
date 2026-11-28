// Live notes state synced from Ktor backend
let mockNotes = [];

// Track UI state
let selectedColor = "blue";
let activeFilter = "all";
let searchQuery = "";
const likedNoteIds = new Set(
  JSON.parse(localStorage.getItem("dropnote-liked-ids") || "[]")
);
const MAX_FILE_SIZE_MB = 10;
const isAndroidHost = navigator.userAgent.includes("DropNoteHost");

// Persistent device ID shared across tabs on the same browser
let clientId = localStorage.getItem("dropnote-client-id");
if (!clientId) {
  clientId = isAndroidHost ? "host-app" : "web-" + Math.random().toString(36).substring(2, 10);
  localStorage.setItem("dropnote-client-id", clientId);
}

// View & Theme Elements
const landingView = document.getElementById("landing-view");
const dashboardView = document.getElementById("dashboard-view");
const enterBoardBtn = document.getElementById("enter-board-btn");
const backToLanding = document.getElementById("back-to-landing");
const themeToggleBtn = document.getElementById("theme-toggle-btn");
const landingThemeBtn = document.getElementById("landing-theme-btn");
const connectionBanner = document.getElementById("connection-banner");

// Form & Attachment Elements
const noteForm = document.getElementById("note-form");
const authorInput = document.getElementById("author-input");
const messageInput = document.getElementById("message-input");
const submitBtn = noteForm.querySelector('button[type="submit"]');
const colorSwatches = document.querySelectorAll(".color-swatch");

const attachmentInput = document.getElementById("attachment-input");
const fileLabelText = document.getElementById("file-label-text");
const uploadPreviewBox = document.getElementById("upload-preview-box");
const uploadPreviewImg = document.getElementById("upload-preview-img");
const uploadFileChip = document.getElementById("upload-file-chip");
const uploadFileName = document.getElementById("upload-file-name");
const uploadFileSize = document.getElementById("upload-file-size");
const removeAttachmentBtn = document.getElementById("remove-attachment-btn");

// Feed & Filter Elements
const notesBoard = document.getElementById("notes-board");
const noteCount = document.getElementById("note-count");
const refreshBtn = document.getElementById("refresh-btn");
const searchInput = document.getElementById("search-input");
const filterPills = document.querySelectorAll(".filter-pill");

// Modal Elements
const imageModal = document.getElementById("image-modal");
const modalImg = document.getElementById("modal-img");
const closeModal = document.getElementById("close-modal");
const downloadModalBtn = document.getElementById("download-modal-btn");

function formatBytes(bytes) {
  if (!bytes) return "";
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function isImageAttachment(note) {
  if (typeof note.isImage === "boolean") return note.isImage;
  const target = (note.fileName || note.fileUrl || "").toLowerCase();
  return /\.(jpg|jpeg|png|gif|webp|bmp|svg)$/.test(target);
}

// 1. Dark Mode & Saved Guest Name Setup
function applyTheme(isDark) {
  document.body.classList.toggle("dark-mode", isDark);
  const icon = isDark ? "\u2600" : "\u263E";
  themeToggleBtn.textContent = icon;
  landingThemeBtn.textContent = icon;
}

const savedTheme = localStorage.getItem("dropnote-dark") === "true";
applyTheme(savedTheme);

const savedAuthor = localStorage.getItem("dropnote-author");
if (savedAuthor) {
  authorInput.value = savedAuthor;
}

function toggleTheme() {
  const isDark = !document.body.classList.contains("dark-mode");
  localStorage.setItem("dropnote-dark", isDark);
  applyTheme(isDark);
}

// Exposed for the Android host WebView so the app's single theme button controls both
window.setHostDarkMode = function (isDark) {
  localStorage.setItem("dropnote-dark", isDark);
  applyTheme(Boolean(isDark));
  if (themeToggleBtn) themeToggleBtn.style.display = "none";
  if (landingThemeBtn) landingThemeBtn.style.display = "none";
};

themeToggleBtn.addEventListener("click", toggleTheme);
landingThemeBtn.addEventListener("click", toggleTheme);

// 2. Navigation between Landing Page and Dashboard
function showDashboard(pushHistory = true) {
  landingView.classList.add("hidden");
  dashboardView.classList.remove("hidden");
  if (pushHistory) {
    history.pushState({ view: "dashboard" }, "", "#board");
  }
  fetchNotes();
}

function showLanding(pushHistory = true) {
  if (isAndroidHost) return; // Keep host WebView locked to the live board
  dashboardView.classList.add("hidden");
  landingView.classList.remove("hidden");
  if (pushHistory) {
    history.pushState({ view: "landing" }, "", window.location.pathname);
  }
}

enterBoardBtn.addEventListener("click", () => showDashboard(true));
backToLanding.addEventListener("click", () => showLanding(true));

window.addEventListener("popstate", () => {
  if (window.location.hash === "#board") {
    showDashboard(false);
  } else {
    showLanding(false);
  }
});

if (window.location.hash === "#board") {
  showDashboard(false);
}

// 3. Sticky Note Color Picker
colorSwatches.forEach((swatch) => {
  swatch.addEventListener("click", () => {
    colorSwatches.forEach((s) => s.classList.remove("active"));
    swatch.classList.add("active");
    selectedColor = swatch.dataset.color;
  });
});

// 4. Photo & File Preview + 10MB Size Check
attachmentInput.addEventListener("change", () => {
  const file = attachmentInput.files[0];
  if (!file) {
    clearAttachmentSelection();
    return;
  }

  const maxBytes = MAX_FILE_SIZE_MB * 1024 * 1024;
  if (file.size > maxBytes) {
    alert(`File is too large (${formatBytes(file.size)}). Please choose a photo or file under ${MAX_FILE_SIZE_MB} MB.`);
    clearAttachmentSelection();
    return;
  }

  uploadPreviewBox.classList.remove("hidden");
  fileLabelText.textContent = file.name;

  if (file.type.startsWith("image/")) {
    uploadPreviewImg.src = URL.createObjectURL(file);
    uploadPreviewImg.classList.remove("hidden");
    uploadFileChip.classList.add("hidden");
  } else {
    uploadPreviewImg.classList.add("hidden");
    uploadFileName.textContent = file.name;
    uploadFileSize.textContent = formatBytes(file.size);
    uploadFileChip.classList.remove("hidden");
  }
});

removeAttachmentBtn.addEventListener("click", clearAttachmentSelection);

function clearAttachmentSelection() {
  attachmentInput.value = "";
  uploadPreviewImg.src = "";
  uploadPreviewImg.classList.add("hidden");
  uploadFileChip.classList.add("hidden");
  uploadPreviewBox.classList.add("hidden");
  fileLabelText.textContent = "+ Attach Photo or File (Optional)";
}

// 5. Search & Filter Listeners
searchInput.addEventListener("input", (e) => {
  searchQuery = e.target.value.toLowerCase().trim();
  renderNotes(mockNotes);
});

filterPills.forEach((pill) => {
  pill.addEventListener("click", () => {
    filterPills.forEach((p) => p.classList.remove("active"));
    pill.classList.add("active");
    activeFilter = pill.dataset.filter;
    renderNotes(mockNotes);
  });
});

async function copyTextToClipboard(text) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
  } else {
    const tempInput = document.createElement("textarea");
    tempInput.value = text;
    tempInput.style.position = "fixed";
    tempInput.style.opacity = "0";
    document.body.appendChild(tempInput);
    tempInput.select();
    document.execCommand("copy");
    document.body.removeChild(tempInput);
  }
}

// 6. Render Note Cards
function renderNotes(notes) {
  notesBoard.innerHTML = "";
  noteCount.textContent = notes.length;

  const filteredNotes = notes.filter((note) => {
    const fileLabel = (note.fileName || "").toLowerCase();
    const matchesSearch =
      note.author.toLowerCase().includes(searchQuery) ||
      note.message.toLowerCase().includes(searchQuery) ||
      fileLabel.includes(searchQuery);

    if (!matchesSearch) return false;

    const hasAttachment = Boolean(note.fileUrl);
    const isImg = hasAttachment && isImageAttachment(note);

    if (activeFilter === "photos") return isImg;
    if (activeFilter === "files") return hasAttachment && !isImg;
    if (activeFilter === "liked") return likedNoteIds.has(note.id) || (note.likes || 0) > 0;
    return true;
  });

  if (filteredNotes.length === 0) {
    notesBoard.innerHTML = "<p style='color:var(--text-muted);'>No matching notes found.</p>";
    return;
  }

  filteredNotes.forEach((note) => {
    const card = document.createElement("div");
    card.className = `note-card color-${note.color || "blue"}`;

    const cardTop = document.createElement("div");
    cardTop.className = "card-top";

    const avatar = document.createElement("div");
    avatar.className = "avatar-circle";
    avatar.textContent = (note.author || "G").charAt(0).toUpperCase();

    const meta = document.createElement("div");
    meta.className = "card-meta";

    const title = document.createElement("h4");
    title.textContent = note.author || "Guest";

    const time = document.createElement("span");
    time.className = "timestamp";
    time.textContent = note.timestamp;

    meta.appendChild(title);
    meta.appendChild(time);
    cardTop.appendChild(avatar);
    cardTop.appendChild(meta);
    card.appendChild(cardTop);

    if (note.message) {
      const text = document.createElement("p");
      text.textContent = note.message;
      card.appendChild(text);
    }

    if (note.fileUrl) {
      if (isImageAttachment(note)) {
        const img = document.createElement("img");
        img.src = note.fileUrl;
        img.alt = note.fileName || "Note attachment";
        img.addEventListener("click", () => {
          modalImg.src = note.fileUrl;
          downloadModalBtn.href = note.fileUrl;
          downloadModalBtn.download = note.fileName || `dropnote-${note.id}.png`;
          imageModal.classList.remove("hidden");
        });
        card.appendChild(img);
      } else {
        const fileLink = document.createElement("a");
        fileLink.className = "file-chip";
        fileLink.href = note.fileUrl;
        fileLink.download = note.fileName || `dropnote-file-${note.id}`;

        const icon = document.createElement("span");
        icon.className = "file-icon";
        icon.innerHTML = "&#128196;";

        const fileMeta = document.createElement("div");
        fileMeta.className = "file-meta";

        const nameEl = document.createElement("strong");
        nameEl.textContent = note.fileName || "Attached File";

        const sizeEl = document.createElement("span");
        sizeEl.textContent = note.fileSize || "Tap to download";

        const dlBadge = document.createElement("span");
        dlBadge.className = "file-dl-badge";
        dlBadge.innerHTML = "&#8681; Save";

        fileMeta.appendChild(nameEl);
        fileMeta.appendChild(sizeEl);
        fileLink.appendChild(icon);
        fileLink.appendChild(fileMeta);
        fileLink.appendChild(dlBadge);
        card.appendChild(fileLink);
      }
    }

    const footer = document.createElement("div");
    footer.className = "card-footer";

    const copyBtn = document.createElement("button");
    copyBtn.type = "button";
    copyBtn.className = "action-chip";
    copyBtn.innerHTML = "&#128203; Copy";
    copyBtn.addEventListener("click", async () => {
      await copyTextToClipboard(note.message || note.fileName || "");
      copyBtn.classList.add("copied");
      copyBtn.innerHTML = "&#10003; Copied";
      setTimeout(() => {
        copyBtn.classList.remove("copied");
        copyBtn.innerHTML = "&#128203; Copy";
      }, 1800);
    });

    const reactBtn = document.createElement("button");
    reactBtn.type = "button";
    const isLiked = likedNoteIds.has(note.id);
    reactBtn.className = `react-btn ${isLiked ? "liked" : ""}`;
    reactBtn.innerHTML = `${isLiked ? "&#9829;" : "&#9825;"} <span>${note.likes || 0}</span>`;

    reactBtn.addEventListener("click", async () => {
      const nextLiked = !likedNoteIds.has(note.id);
      if (nextLiked) {
        likedNoteIds.add(note.id);
      } else {
        likedNoteIds.delete(note.id);
      }
      localStorage.setItem("dropnote-liked-ids", JSON.stringify([...likedNoteIds]));
      await fetch(`/api/notes/${encodeURIComponent(note.id)}/like?liked=${nextLiked}`, {
        method: "POST"
      });
      await fetchNotes();
    });
    footer.appendChild(copyBtn);
    footer.appendChild(reactBtn);
    card.appendChild(footer);

    notesBoard.appendChild(card);
  });
}

// 7. Fetch Notes from Server
async function fetchNotes() {
  try {
    const response = await fetch(`/api/notes?clientId=${encodeURIComponent(clientId)}`);
    if (!response.ok) {
      throw new Error("Failed to fetch notes");
    }

    const apiNotes = await response.json();

    mockNotes = apiNotes.map((note) => ({
      id: note.id,
      author: note.author || "Guest",
      message: note.text || "",
      color: note.color || "blue",
      likes: note.likes || 0,
      fileUrl: note.fileUrl || null,
      fileName: note.fileName || null,
      fileSize: note.fileSize || null,
      isImage: Boolean(note.isImage),
      timestamp: new Date(note.timestamp).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit"
      })
    }));

    connectionBanner.classList.add("hidden");
    renderNotes(mockNotes);
  } catch (err) {
    console.error(err);
    connectionBanner.classList.remove("hidden");
  }
}

// 8. Submit New Note + Optional Photo/File Attachment
noteForm.addEventListener("submit", async (e) => {
  e.preventDefault();

  const author = authorInput.value.trim();
  const message = messageInput.value.trim();
  const file = attachmentInput.files[0];

  if (!author || (!message && !file)) return;

  localStorage.setItem("dropnote-author", author);

  submitBtn.disabled = true;
  submitBtn.textContent = file ? "Uploading..." : "Posting...";

  try {
    let attachmentData = {
      fileUrl: null,
      fileName: null,
      fileSize: null,
      isImage: false
    };

    if (file) {
      const formData = new FormData();
      formData.append("file", file);

      const uploadResp = await fetch("/api/upload", {
        method: "POST",
        body: formData
      });

      if (!uploadResp.ok) {
        throw new Error("File upload failed");
      }

      attachmentData = await uploadResp.json();
    }

    const response = await fetch("/api/notes", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        author: author,
        text: message,
        color: selectedColor,
        fileUrl: attachmentData.fileUrl,
        fileName: attachmentData.fileName,
        fileSize: attachmentData.fileSize,
        isImage: attachmentData.isImage
      })
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || "Failed to post note");
    }

    messageInput.value = "";
    clearAttachmentSelection();
    await fetchNotes();
  } catch (err) {
    console.error(err);
    alert(err.message || "Could not post note. Please check the server.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = "Post to Board";
  }
});

// 9. Refresh & Modal Controls
refreshBtn.addEventListener("click", fetchNotes);

setInterval(() => {
  fetchNotes();
}, 2000);

closeModal.addEventListener("click", () => {
  imageModal.classList.add("hidden");
});

imageModal.addEventListener("click", (e) => {
  if (e.target === imageModal) {
    imageModal.classList.add("hidden");
  }
});

fetchNotes();