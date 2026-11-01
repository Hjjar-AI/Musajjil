# py/radar_chart.py
import math
from html import escape

def generate_radar_chart(criteria_names, scores, size=300):
    n = len(criteria_names)
    if n < 3:
        return '<div class="alert alert-warning">عدد المحاور غير كافٍ لعرض مخطط رادار (يحتاج 3 على الأقل).</div>'
    
    # ترميز أسماء المعايير (منع XSS)
    escaped_names = [escape(name) for name in criteria_names]
    
    cx = cy = size / 2
    radius = size * 0.38
    angles = [2 * math.pi * i / n - math.pi/2 for i in range(n)]
    max_score = 5.0
    svg = f'<svg viewBox="0 0 {size} {size}" xmlns="http://www.w3.org/2000/svg" class="radar-chart" style="width:{size}px;height:{size}px;max-width:100%;">'
    
    # Grid
    for level in range(1, 6):
        r = radius * level / max_score
        pts = []
        for ang in angles:
            x = cx + r * math.cos(ang)
            y = cy + r * math.sin(ang)
            pts.append(f"{x:.1f},{y:.1f}")
        svg += f'<polygon points="{" ".join(pts)}" fill="none" stroke="#dee2e6" stroke-width="1"/>'
    
    # Axes & labels (استخدام الأسماء المرمزة)
    for i, ang in enumerate(angles):
        x2 = cx + radius * math.cos(ang)
        y2 = cy + radius * math.sin(ang)
        svg += f'<line x1="{cx}" y1="{cy}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="#adb5bd" stroke-width="1"/>'
        lx = cx + (radius + 20) * math.cos(ang)
        ly = cy + (radius + 20) * math.sin(ang)
        svg += f'<text x="{lx:.1f}" y="{ly:.1f}" text-anchor="middle" dominant-baseline="middle" font-size="12" fill="currentColor">{escaped_names[i]}</text>'
    
    # Data polygon
    pts = []
    for i, score in enumerate(scores):
        r = radius * score / max_score
        x = cx + r * math.cos(angles[i])
        y = cy + r * math.sin(angles[i])
        pts.append(f"{x:.1f},{y:.1f}")
    svg += f'<polygon points="{" ".join(pts)}" fill="rgba(var(--bs-primary-rgb, 13,110,253), 0.3)" stroke="var(--bs-primary, #0d6efd)" stroke-width="2"/>'
    for pt in pts:
        xy = pt.split(',')
        svg += f'<circle cx="{xy[0]}" cy="{xy[1]}" r="3" fill="var(--bs-primary, #0d6efd)"/>'
    svg += '</svg>'
    return svg