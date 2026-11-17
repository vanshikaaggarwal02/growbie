// Plain-language labels and explanations for the numbers shown to users.

export const PAGESPEED_SCORES = {
  performance: ['Performance', 'How quickly the page loads and becomes usable on a mid-range phone.'],
  accessibility: ['Accessibility', 'Automated checks for image descriptions, colour contrast and form labels that help people using screen readers. Automated tests catch only part of the problems.'],
  bestPractices: ['Best practices', 'Basic web hygiene: a secure connection, no known-vulnerable code, no errors in the browser.'],
  seo: ['SEO basics', 'Technical basics search engines look for, such as a title, a description and links they can follow. It does not predict rankings.'],
}

// Google's published "good" thresholds for each timing.
export const PAGESPEED_METRICS = {
  firstContentfulPaint: ['First content appears', 'When the first text or image shows on screen. Under 1.8 s is good.'],
  largestContentfulPaint: ['Main content appears', 'When the biggest element, usually the main image or heading, is on screen. Under 2.5 s is good.'],
  totalBlockingTime: ['Time the page froze', 'How long the page ignored taps and clicks while loading. Under 200 ms is good.'],
  cumulativeLayoutShift: ['Page jumping', 'How much the layout moves around while loading. Under 0.1 is good.'],
  speedIndex: ['Speed Index', 'How quickly the visible part of the page fills in. Under 3.4 s is good.'],
}

export function scoreBand(score) {
  if (score == null) return ['Unavailable', 'na']
  if (score >= 90) return ['Good', 'good']
  if (score >= 50) return ['Needs work', 'mid']
  return ['Poor', 'bad']
}

export const SKIP_REASON = {
  ROBOTS_DISALLOWED: 'Blocked by the site\'s robots.txt',
  FETCH_FAILED: 'Could not be fetched',
  HTTP_ERROR: 'The page returned an error',
}

export const CATEGORY = { SEO: 'SEO', CONTENT: 'Content', TECHNICAL: 'Technical', ACCESSIBILITY: 'Accessibility', PERFORMANCE: 'Performance', CONVERSION: 'Conversion', TRUST: 'Trust' }
export const prettyKey = (k) => k.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())
