// Temporary mock notes (will be replaced by Aeron's GET /notes API)
let mockNotes = [
  {
    id: 1,
    author: "Carl",
    message: "Welcome to DropNote! Drop a note, attach a photo, or share documents with everyone in the room.",
    color: "blue",
    likes: 1,
    fileUrl: null,
    fileName: null,
    fileSize: null,
    isImage: false,
    timestamp: "Just now"
  }
];

// Track UI state
let selectedColor = "blue";
let activeFilter = "all";
let searchQuery = "";
const likedNoteIds = new Set();
const MAX_FILE_SIZE_MB = 10;

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

// Helper: Format byte sizes nicely (KB / MB)
function formatBytes(bytes) {
  if (!bytes) return "";
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Helper: Check if a filename or MIME type is an image
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

themeToggleBtn.addEventListener("click", toggleTheme);
landingThemeBtn.addEventListener("click", toggleTheme);

// 2. Navigation between Landing Page and Dashboard (Syncs with Browser Back Button)
function showDashboard(pushHistory = true) {
  landingView.classList.add("hidden");
  dashboardView.classList.remove("hidden");
  if (pushHistory) {
    history.pushState({ view: "dashboard" }, "", "#board");
  }
  fetchNotes();
}

function showLanding(pushHistory = true) {
  dashboardView.classList.add("hidden");
  landingView.classList.remove("hidden");
  if (pushHistory) {
    history.pushState({ view: "landing" }, "", window.location.pathname);
  }
}

enterBoardBtn.addEventListener("click", () => showDashboard(true));

// Optional: Clicking the DN logo returns to landing page
backToLanding.addEventListener("click", () => showLanding(true));

// Listen for phone/browser Back and Forward buttons
window.addEventListener("popstate", () => {
  if (window.location.hash === "#board") {
    showDashboard(false);
  } else {
    showLanding(false);
  }
});

// Load the right view if the user refreshes while on #board
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

// Helper: Copy text safely on both HTTPS and local HTTP IP addresses
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

// 6. Render Note Cards (Applies active search & filters)
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

    // Card Header (Avatar + Author + Time)
    const cardTop = document.createElement("div");
    cardTop.className = "card-top";

    const avatar = document.createElement("div");
    avatar.className = "avatar-circle";
    avatar.textContent = note.author.charAt(0).toUpperCase();

    const meta = document.createElement("div");
    meta.className = "card-meta";

    const title = document.createElement("h4");
    title.textContent = note.author;

    const time = document.createElement("span");
    time.className = "timestamp";
    time.textContent = note.timestamp;

    meta.appendChild(title);
    meta.appendChild(time);
    cardTop.appendChild(avatar);
    cardTop.appendChild(meta);
    card.appendChild(cardTop);

    // Note Message
    const text = document.createElement("p");
    text.textContent = note.message;
    card.appendChild(text);

    // Attachment Rendering (Image Lightbox OR Downloadable File Chip)
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

    // Card Footer (Copy + Reaction)
    const footer = document.createElement("div");
    footer.className = "card-footer";

    const copyBtn = document.createElement("button");
    copyBtn.type = "button";
    copyBtn.className = "action-chip";
    copyBtn.innerHTML = "&#128203; Copy";
    copyBtn.addEventListener("click", async () => {
      await copyTextToClipboard(note.message);
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

    reactBtn.addEventListener("click", () => {
      if (likedNoteIds.has(note.id)) {
        likedNoteIds.delete(note.id);
        note.likes = Math.max(0, (note.likes || 1) - 1);
      } else {
        likedNoteIds.add(note.id);
        note.likes = (note.likes || 0) + 1;
      }
      // TODO: Optional fetch(`/notes/${note.id}/like`, { method: 'POST' })
      renderNotes(mockNotes);
    });

    footer.appendChild(copyBtn);
    footer.appendChild(reactBtn);
    card.appendChild(footer);

    notesBoard.appendChild(card);
  });
}

async function fetchNotes() {
  try {
    const response = await fetch("/api/notes");

    if (!response.ok) {
      throw new Error("Failed to fetch notes");
    }

    const apiNotes = await response.json();

    mockNotes = apiNotes.map(note => ({
      id: note.id,
      author: "Guest",
      message: note.text,
      color: "blue",
      likes: 0,
      fileUrl: null,
      fileName: null,
      fileSize: null,
      isImage: false,
      timestamp: new Date(note.timestamp).toLocaleString()
    }));

    connectionBanner.classList.add("hidden");
    renderNotes(mockNotes);

  } catch (err) {
    console.error(err);
    connectionBanner.classList.remove("hidden");
  }
}

// 8. Submit New Note (Hook up Aeron's POST /notes endpoint here later)
noteForm.addEventListener("submit", async (e) => {
  e.preventDefault();

  const author = authorInput.value.trim();
  const message = messageInput.value.trim();

  if (!author || !message) return;

  if (attachmentInput.files.length > 0) {
    alert("Attachments are not supported by the server yet.");
    return;
  }

  localStorage.setItem("dropnote-author", author);

  submitBtn.disabled = true;
  submitBtn.textContent = "Posting...";

  try {
    const response = await fetch("/api/notes", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ text: message })
    });

    if (!response.ok) {
      throw new Error("Failed to post note");
    }

    messageInput.value = "";
    await fetchNotes();

  } catch (err) {
    console.error(err);
    alert("Could not post note. Please check the server.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = "Post to Board";
  }
});

// 9. Refresh & Modal Controls
refreshBtn.addEventListener("click", fetchNotes);

setInterval(() => {
  if (!dashboardView.classList.contains("hidden")) {
    fetchNotes();
  }
}, 5000);

closeModal.addEventListener("click", () => {
  imageModal.classList.add("hidden");
});

imageModal.addEventListener("click", (e) => {
  if (e.target === imageModal) {
    imageModal.classList.add("hidden");
  }
});

// Initialize count on load
fetchNotes();