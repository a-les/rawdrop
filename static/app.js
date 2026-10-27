document.addEventListener("DOMContentLoaded", () => {
  // --- UI Elements ---
  const urlInput = document.getElementById("urlInput");
  const pasteBtn = document.getElementById("pasteBtn");
  const fetchBtn = document.getElementById("fetchBtn");
  const errorBox = document.getElementById("errorBox");
  const errorMessage = document.getElementById("errorMessage");

  const podProgressSlot = document.getElementById("podProgressSlot");
  const podProgressBar = document.getElementById("podProgressBar");
  const podProgressText = document.getElementById("podProgressText");

  const previewFlyout = document.getElementById("previewFlyout");
  const videoCover = document.getElementById("videoCover");
  const videoDuration = document.getElementById("videoDuration");
  const videoTitle = document.getElementById("videoTitle");
  const videoAuthor = document.getElementById("videoAuthor");
  const instantDownloadBtn = document.getElementById("instantDownloadBtn");

  const historyFeed = document.getElementById("historyFeed");
  const historyCount = document.getElementById("historyCount");
  const openFolderHeaderBtn = document.getElementById("openFolderHeaderBtn");

  // Graph Nodes
  const nodeRoot = document.getElementById("nodeRoot");
  const nodeRootLabel = document.getElementById("nodeRootLabel");
  const nodeRedirect = document.getElementById("nodeRedirect");
  const nodeBypass = document.getElementById("nodeBypass");
  const nodeQuality = document.getElementById("nodeQuality");
  const nodeStorage = document.getElementById("nodeStorage");

  let currentVideoUrl = "";

  // --- Pipeline Animation States (с полным сбросом для повторных скачиваний) ---
  function setPipelineStage(stage) {
    const nodes = [nodeRoot, nodeRedirect, nodeBypass, nodeQuality, nodeStorage];

    // ВСЕГДА сначала очищаем предыдущие статусы (done / active)
    nodes.forEach((n) => n.classList.remove("active", "done"));

    if (stage === "idle") {
      nodeRootLabel.textContent = "Idle (Waiting for link)";
    } else if (stage === "fetching") {
      nodeRoot.classList.add("active");
      nodeRedirect.classList.add("active");
      nodeRootLabel.textContent = "Parsing Source URL...";
    } else if (stage === "ready") {
      nodeRoot.classList.add("done");
      nodeRedirect.classList.add("done");

      nodeBypass.classList.add("active");
      nodeQuality.classList.add("active");
      nodeRootLabel.textContent = "Stream Verified";
    } else if (stage === "downloading") {
      nodeRoot.classList.add("done");
      nodeRedirect.classList.add("done");
      nodeBypass.classList.add("active");
      nodeQuality.classList.add("active");
      nodeStorage.classList.add("active");
      nodeRootLabel.textContent = "Downloading Stream...";
    } else if (stage === "done") {
      nodes.forEach((n) => n.classList.add("done"));
      nodeRootLabel.textContent = "Extraction Completed";
    }
  }

  // --- Helpers ---
  function formatDuration(sec) {
    if (!sec) return "00:00";
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  }

  function showError(msg) {
    const cleanMsg = (msg || "").replace(/\u001b\[.*?m|\x1b\[.*?m|≡\[.*?m/g, "").trim();
    errorMessage.textContent = cleanMsg;
    errorBox.classList.remove("hidden");
  }

  function clearError() {
    errorBox.classList.add("hidden");
    errorMessage.textContent = "";
  }

  function setLoading(loading) {
    const arrow = fetchBtn.querySelector(".submit-arrow-icon");
    const spinner = fetchBtn.querySelector(".spinner");
    if (loading) {
      fetchBtn.disabled = true;
      arrow.classList.add("hidden");
      spinner.classList.remove("hidden");
    } else {
      fetchBtn.disabled = false;
      arrow.classList.remove("hidden");
      spinner.classList.add("hidden");
    }
  }

  // --- Load Recent Extractions History ---
  async function loadHistory() {
    try {
      const res = await fetch("/api/history");
      if (!res.ok) return;
      const data = await res.json();
      const items = data.items || [];

      historyCount.textContent = `${items.length} saved`;

      if (items.length === 0) {
        historyFeed.innerHTML = `<div class="timeline-empty">No extractions yet. Paste a link below.</div>`;
        return;
      }

      historyFeed.innerHTML = items
        .map(
          (item) => `
        <div class="timeline-item" data-path="${encodeURIComponent(item.path)}" title="Click to play / open">
          <div class="timeline-node-pin"></div>
          <div class="timeline-info">
            <span class="timeline-file-title">${item.title}</span>
            <span class="timeline-file-sub">${item.size_mb} MB • ${item.time_ago}</span>
          </div>
          <button class="timeline-action-btn" title="Open file">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
          </button>
        </div>
      `
        )
        .join("");

      historyFeed.querySelectorAll(".timeline-item").forEach((el) => {
        el.addEventListener("click", async () => {
          const filePath = decodeURIComponent(el.getAttribute("data-path"));
          await fetch("/api/open-file", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ path: filePath }),
          });
        });
      });
    } catch (e) {
      console.warn("Could not load history:", e);
    }
  }

  loadHistory();

  // --- Paste Button ---
  pasteBtn.addEventListener("click", async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        urlInput.value = text.trim();
        clearError();
        fetchBtn.click();
      }
    } catch (err) {
      showError("Could not access clipboard. Please paste manually (Ctrl+V).");
    }
  });

  // --- Submit Video URL (Fetch Metadata) ---
  fetchBtn.addEventListener("click", async () => {
    const url = urlInput.value.trim();
    if (!url) {
      showError("Please enter a TikTok video link.");
      return;
    }

    clearError();
    previewFlyout.classList.add("hidden");
    podProgressSlot.classList.add("hidden");
    setLoading(true);
    setPipelineStage("fetching");

    try {
      const res = await fetch("/api/info", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url }),
      });

      const result = await res.json();
      if (!res.ok) {
        throw new Error(result.detail || "Unable to extract video stream.");
      }

      const data = result.data;
      currentVideoUrl = url;

      videoTitle.textContent = data.title || "Untitled Video";
      videoAuthor.textContent = data.author ? `@${data.author}` : "@creator";
      videoDuration.textContent = formatDuration(data.duration);
      videoCover.src = data.cover_url || "";

      setPipelineStage("ready");
      previewFlyout.classList.remove("hidden");
    } catch (err) {
      setPipelineStage("idle");
      showError(err.message || "Network error while connecting to stream.");
    } finally {
      setLoading(false);
    }
  });

  // --- Start Extraction (SSE Download) ---
  instantDownloadBtn.addEventListener("click", () => {
    if (!currentVideoUrl) return;

    previewFlyout.classList.add("hidden");
    podProgressSlot.classList.remove("hidden");
    podProgressBar.style.width = "0%";
    podProgressText.textContent = "0%";
    setPipelineStage("downloading");

    const sseUrl = `/api/download-stream?url=${encodeURIComponent(currentVideoUrl)}`;
    const eventSource = new EventSource(sseUrl);

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);

        if (data.status === "downloading") {
          const p = data.percent || 0;
          podProgressBar.style.width = `${p}%`;
          podProgressText.textContent = `${p}% (${data.speed_str || "--"})`;
        } else if (data.status === "done") {
          eventSource.close();
          podProgressBar.style.width = "100%";
          podProgressText.textContent = "Saved!";
          setPipelineStage("done");

          setTimeout(() => {
            loadHistory();
            podProgressSlot.classList.add("hidden");
          }, 1500);
        } else if (data.status === "error") {
          eventSource.close();
          podProgressSlot.classList.add("hidden");
          setPipelineStage("idle");
          showError(`Extraction failed: ${data.message}`);
        }
      } catch (err) {
        console.error("SSE parse error:", err);
      }
    };

    eventSource.onerror = () => {
      eventSource.close();
      podProgressSlot.classList.add("hidden");
      setPipelineStage("idle");
      showError("Connection dropped during video transfer.");
    };
  });

  // --- Open Downloads Folder ---
  openFolderHeaderBtn.addEventListener("click", async () => {
    try {
      await fetch("/api/open-folder", { method: "POST" });
    } catch (e) {
      showError("Could not open downloads directory.");
    }
  });

  // Enter key trigger
  urlInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      fetchBtn.click();
    }
  });
});