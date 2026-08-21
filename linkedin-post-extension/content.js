// Wait for the page to load
(function() {
  'use strict';

  // Styling lives in styles.css, which the manifest injects alongside this script.

  // Checked in order, so the button lands in the most specific bar the post offers.
  const ACTION_BAR_SELECTORS = [
    '.feed-shared-control-menu',
    '.social-details-social-counts',
    '.feed-shared-update-v2__actions',
    '.update-v2-social-activity',
    '.engage-with-content',
    '.comments-comment-item__actions',
    '.feed-shared-update-v2__comments',
    '.social-details',
    '.feed-shared-social-action-bar'
  ];

  // JobEmailer can only fetch the public /posts/ permalink; /feed/update/ bounces anonymous
  // requests to LinkedIn's signup wall. Prefer the form the backend can actually read.
  const POST_URL_SELECTORS = [
    'a[href*="/posts/"]',
    'a[href*="/feed/update/"]',
    'a[href*="/activity/"]'
  ];

  const POST_SELECTOR = 'div.feed-shared-update-v2:not(.sent-button-added), li[class*="feed-item"]:not(.sent-button-added)';

  // A post is skipped, not marked done, while its action bar is still rendering. This bounds how
  // long we keep re-checking one that never grows one.
  const MAX_ANCHOR_ATTEMPTS = 40;
  const RESCAN_DEBOUNCE_MS = 250;

  function cleanUrl(href) {
    try {
      const url = new URL(href, window.location.href);
      url.hash = '';
      url.search = ''; // drop trk/utm tracking params
      return url.toString();
    } catch (e) {
      return href;
    }
  }

  function findPostUrl(container) {
    for (const selector of POST_URL_SELECTORS) {
      const link = container.querySelector(selector);
      if (link && link.href) {
        return cleanUrl(link.href);
      }
    }
    const anyLink = container.querySelector('[href*="linkedin.com"]');
    if (anyLink && anyLink.href) {
      return cleanUrl(anyLink.href);
    }
    return cleanUrl(window.location.href);
  }

  function findActionBar(container) {
    for (const selector of ACTION_BAR_SELECTORS) {
      const bar = container.querySelector(selector);
      if (bar) {
        return bar;
      }
    }
    return null;
  }

  function createSentButton(postUrl) {
    const sentButton = document.createElement('button');
    sentButton.className = 'sent-button';
    sentButton.type = 'button';
    sentButton.textContent = 'Sent';
    sentButton.setAttribute('data-post-url', postUrl);

    sentButton.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      showPostLinkPopup(this.getAttribute('data-post-url'));
    });

    return sentButton;
  }

  // Function to add "Sent" buttons to posts
  function addSentButtons() {
    // Target the main feed container
    const feedContainer = document.querySelector('div.scaffold-fc-content main');
    if (!feedContainer) return;

    // Select all post containers (based on the HTML structure you provided)
    const postContainers = feedContainer.querySelectorAll(POST_SELECTOR);

    postContainers.forEach(container => {
      const actionBar = findActionBar(container);

      if (!actionBar) {
        // Leave the post unmarked so a bar that renders a beat later still gets a button.
        const attempts = Number(container.dataset.sentButtonAttempts || 0) + 1;
        container.dataset.sentButtonAttempts = String(attempts);
        if (attempts >= MAX_ANCHOR_ATTEMPTS) {
          container.classList.add('sent-button-added');
        }
        return;
      }

      container.classList.add('sent-button-added');

      // Check if we've already added a button to this action bar
      if (!actionBar.querySelector('.sent-button')) {
        actionBar.appendChild(createSentButton(findPostUrl(container)));
      }
    });
  }

  function copyLink(linkInput, copyBtn) {
    const done = () => {
      copyBtn.textContent = 'Copied!';
      setTimeout(() => {
        copyBtn.textContent = 'Copy Link';
      }, 2000);
    };

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(linkInput.value).then(done, () => {
        linkInput.select();
        document.execCommand('copy');
        done();
      });
      return;
    }

    linkInput.select();
    document.execCommand('copy');
    done();
  }

  // Function to show the post link popup
  function showPostLinkPopup(postUrl) {
    // Remove any existing popup
    const existingPopup = document.getElementById('linkedin-post-popup');
    if (existingPopup) {
      existingPopup.remove();
    }

    // Create popup container
    const popup = document.createElement('div');
    popup.id = 'linkedin-post-popup';
    popup.className = 'linkedin-post-popup';

    // Build the content as nodes so a url can never be parsed as markup
    const content = document.createElement('div');
    content.className = 'linkedin-post-popup-content';
    content.innerHTML = `
      <div class="linkedin-post-popup-header">
        <h3>Post Link</h3>
        <span class="linkedin-post-popup-close">&times;</span>
      </div>
      <div class="linkedin-post-popup-body">
        <input type="text" id="post-link-input" readonly />
        <button id="copy-link-btn" type="button">Copy Link</button>
      </div>
    `;
    popup.appendChild(content);

    // Add to document
    document.body.appendChild(popup);

    // Focus the input and select the text
    const linkInput = popup.querySelector('#post-link-input');
    linkInput.value = postUrl;
    linkInput.focus();
    linkInput.select();

    // Add event listeners
    const closeBtn = popup.querySelector('.linkedin-post-popup-close');
    closeBtn.addEventListener('click', () => {
      popup.remove();
    });

    const copyBtn = popup.querySelector('#copy-link-btn');
    copyBtn.addEventListener('click', () => copyLink(linkInput, copyBtn));

    // Close popup when clicking outside
    popup.addEventListener('click', (e) => {
      if (e.target === popup) {
        popup.remove();
      }
    });

    // Close popup when pressing Escape key
    const escapeHandler = (e) => {
      if (e.key === 'Escape') {
        popup.remove();
        document.removeEventListener('keydown', escapeHandler);
      }
    };
    document.addEventListener('keydown', escapeHandler);
  }

  // Initialize the extension
  function init() {
    // Add buttons initially
    addSentButtons();

    let rescanTimer = null;
    const scheduleRescan = () => {
      if (rescanTimer) return;
      rescanTimer = setTimeout(() => {
        rescanTimer = null;
        addSentButtons();
      }, RESCAN_DEBOUNCE_MS);
    };

    // LinkedIn streams posts in and fills each one out over several mutations, so rescan on any
    // element being added rather than only when the post container itself appears.
    const observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        if (mutation.type !== 'childList') continue;
        for (const node of mutation.addedNodes) {
          if (node.nodeType === Node.ELEMENT_NODE) {
            scheduleRescan();
            return;
          }
        }
      }
    });

    // Start observing
    observer.observe(document.body, {
      childList: true,
      subtree: true
    });
  }

  // Run initialization when the page is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
