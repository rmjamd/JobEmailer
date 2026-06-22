// Wait for the page to load
(function() {
  'use strict';

  // Add styles directly to the page
  const style = document.createElement('style');
  style.textContent = `
    .sent-button {
      background-color: #0077b5; /* LinkedIn blue */
      color: white;
      border: none;
      border-radius: 16px;
      padding: 6px 12px;
      margin: 0 5px;
      cursor: pointer;
      font-size: 12px;
      font-weight: 600;
      transition: background-color 0.3s;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .sent-button:hover {
      background-color: #005885; /* Darker LinkedIn blue */
    }

    .linkedin-post-popup {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background-color: rgba(0, 0, 0, 0.5);
      z-index: 10000;
      display: flex;
      justify-content: center;
      align-items: center;
    }

    .linkedin-post-popup-content {
      background-color: white;
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      width: 400px;
      max-width: 90%;
    }

    .linkedin-post-popup-header {
      padding: 16px;
      border-bottom: 1px solid #e0e0e0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .linkedin-post-popup-header h3 {
      margin: 0;
      font-size: 18px;
      color: #333;
    }

    .linkedin-post-popup-close {
      font-size: 24px;
      cursor: pointer;
      color: #999;
    }

    .linkedin-post-popup-close:hover {
      color: #333;
    }

    .linkedin-post-popup-body {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    #post-link-input {
      padding: 10px;
      border: 1px solid #ccc;
      border-radius: 4px;
      font-size: 14px;
      width: 100%;
      box-sizing: border-box;
    }

    #copy-link-btn {
      background-color: #0077b5;
      color: white;
      border: none;
      border-radius: 4px;
      padding: 10px;
      cursor: pointer;
      font-size: 16px;
      font-weight: 600;
      transition: background-color 0.3s;
    }

    #copy-link-btn:hover {
      background-color: #005885;
    }
  `;
  document.head.appendChild(style);

  // Function to add "Sent" buttons to posts
  function addSentButtons() {
    // Target the main feed container
    const feedContainer = document.querySelector('div.scaffold-fc-content main');
    if (!feedContainer) return;

    // Select all post containers (based on the HTML structure you provided)
    const postContainers = feedContainer.querySelectorAll('div.feed-shared-update-v2:not(.sent-button-added), li[class*="feed-item"]:not(.sent-button-added)');

    postContainers.forEach(container => {
      // Mark as processed to avoid duplicates
      container.classList.add('sent-button-added');

      // Try to find the post link
      let postUrl = '';
      const postLinkElement = container.querySelector('a[href*="/feed/update/"], a[href*="/posts/"], a[href*="/activity/"]');
      if (postLinkElement) {
        postUrl = postLinkElement.href;
      } else {
        // Alternative approach: try to get the permalink from data attributes
        const permalinkElement = container.querySelector('[href*="linkedin.com"]');
        if (permalinkElement) {
          postUrl = permalinkElement.href;
        }
      }

      // If we couldn't find a specific post URL, use the current page URL
      if (!postUrl) {
        postUrl = window.location.href;
      }

      // Look for the reaction bar or engagement bar where we can add the button
      const reactionBar = container.querySelector('.feed-shared-control-menu, .social-details-social-counts, .feed-shared-update-v2__actions, .update-v2-social-activity, .engage-with-content');

      if (reactionBar) {
        // Check if we've already added a button to this reaction bar
        if (!reactionBar.querySelector('.sent-button')) {
          // Create the "Sent" button
          const sentButton = document.createElement('button');
          sentButton.className = 'sent-button';
          sentButton.textContent = 'Sent';
          sentButton.setAttribute('data-post-url', postUrl);

          // Add click event
          sentButton.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            const postUrl = this.getAttribute('data-post-url');
            showPostLinkPopup(postUrl);
          });

          // Insert the button at the end of the reaction bar
          reactionBar.appendChild(sentButton);
        }
      } else {
        // Alternative placement: look for a comment/actions area
        const actionsArea = container.querySelector('.comments-comment-item__actions, .feed-shared-update-v2__comments, .social-details, .feed-shared-social-action-bar');
        if (actionsArea && !actionsArea.querySelector('.sent-button')) {
          // Create the "Sent" button
          const sentButton = document.createElement('button');
          sentButton.className = 'sent-button';
          sentButton.textContent = 'Sent';
          sentButton.setAttribute('data-post-url', postUrl);

          // Add click event
          sentButton.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            const postUrl = this.getAttribute('data-post-url');
            showPostLinkPopup(postUrl);
          });

          // Insert the button
          actionsArea.appendChild(sentButton);
        }
      }
    });
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

    // Create popup content
    popup.innerHTML = `
      <div class="linkedin-post-popup-content">
        <div class="linkedin-post-popup-header">
          <h3>Post Link</h3>
          <span class="linkedin-post-popup-close">&times;</span>
        </div>
        <div class="linkedin-post-popup-body">
          <input type="text" id="post-link-input" value="${postUrl}" readonly />
          <button id="copy-link-btn">Copy Link</button>
        </div>
      </div>
    `;

    // Add to document
    document.body.appendChild(popup);

    // Focus the input and select the text
    const linkInput = popup.querySelector('#post-link-input');
    linkInput.focus();
    linkInput.select();

    // Add event listeners
    const closeBtn = popup.querySelector('.linkedin-post-popup-close');
    closeBtn.addEventListener('click', () => {
      popup.remove();
    });

    const copyBtn = popup.querySelector('#copy-link-btn');
    copyBtn.addEventListener('click', () => {
      linkInput.select();
      document.execCommand('copy');
      copyBtn.textContent = 'Copied!';
      setTimeout(() => {
        copyBtn.textContent = 'Copy Link';
      }, 2000);
    });

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

    // Use a more aggressive observer to catch dynamically loaded content
    const observer = new MutationObserver((mutations) => {
      let shouldAddButtons = false;

      mutations.forEach((mutation) => {
        if (mutation.type === 'childList') {
          mutation.addedNodes.forEach((node) => {
            if (node.nodeType === Node.ELEMENT_NODE) {
              // Check if the added node contains posts
              if (node.querySelector && (
                node.querySelector('div.feed-shared-update-v2') ||
                node.classList.contains('feed-shared-update-v2') ||
                node.querySelector('li[class*="feed-item"]')
              )) {
                shouldAddButtons = true;
              }
            }
          });
        }
      });

      if (shouldAddButtons) {
        // Use a small delay to ensure DOM is fully rendered
        setTimeout(addSentButtons, 100);
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