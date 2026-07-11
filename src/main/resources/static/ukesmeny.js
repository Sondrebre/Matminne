function byttDag(knapp) {
    document.querySelectorAll('.dag-tab').forEach(function(t) { t.classList.remove('aktiv'); });
    document.querySelectorAll('.dag-panel').forEach(function(p) { p.classList.remove('aktiv'); });
    knapp.classList.add('aktiv');
    document.getElementById('panel-' + knapp.dataset.idx).classList.add('aktiv');
    oppdaterMobDagNav(parseInt(knapp.dataset.idx, 10));
}

function byttDagIdx(idx) {
    var tab = document.querySelector('.dag-tab[data-idx="' + idx + '"]');
    if (tab) {
        byttDag(tab);
        if (tab.scrollIntoView) tab.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
    }
}

function aktivDagIdx() {
    var aktiv = document.querySelector('.dag-tab.aktiv');
    return aktiv ? parseInt(aktiv.dataset.idx, 10) : 0;
}

function stegDag(delta) {
    var ny = (aktivDagIdx() + delta + 7) % 7;
    byttDagIdx(ny);
}

/* ── UKE-DATOER, UKENUMMER, I DAG-MARKERING ─────────────── */
var DAG_NAVN = ['Mandag','Tirsdag','Onsdag','Torsdag','Fredag','Lørdag','Søndag'];
var MND_KORT = ['jan','feb','mar','apr','mai','jun','jul','aug','sep','okt','nov','des'];

function mandagIUken(d) {
    var dt = new Date(d);
    dt.setHours(0, 0, 0, 0);
    dt.setDate(dt.getDate() - ((dt.getDay() + 6) % 7));
    return dt;
}

function isoUkeNr(d) {
    var dt = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    var dagNr = (dt.getUTCDay() + 6) % 7;
    dt.setUTCDate(dt.getUTCDate() - dagNr + 3);
    var forsteTorsdag = new Date(Date.UTC(dt.getUTCFullYear(), 0, 4));
    forsteTorsdag.setUTCDate(forsteTorsdag.getUTCDate() - ((forsteTorsdag.getUTCDay() + 6) % 7) + 3);
    return { uke: 1 + Math.round((dt - forsteTorsdag) / 604800000), aar: dt.getUTCFullYear() };
}

function oppdaterMobDagNav(idx) {
    var navnEl = document.getElementById('mobDagNavn');
    var datoEl = document.getElementById('mobDagDato');
    if (!navnEl) return;
    var mandag = mandagIUken(new Date());
    var dato = new Date(mandag);
    dato.setDate(mandag.getDate() + idx);
    navnEl.textContent = DAG_NAVN[idx];
    if (datoEl) datoEl.textContent = dato.getDate() + '. ' + MND_KORT[dato.getMonth()];
}

function initUkeDatoer() {
    var iDag = new Date();
    var mandag = mandagIUken(iDag);
    var iDagIdx = (iDag.getDay() + 6) % 7;

    // Grid-kolonner: dato-tall + i dag-markering
    document.querySelectorAll('.uge-dato').forEach(function(el) {
        var idx = parseInt(el.dataset.dagidx, 10);
        var dato = new Date(mandag);
        dato.setDate(mandag.getDate() + idx);
        el.textContent = dato.getDate();
    });
    document.querySelectorAll('.uge-kol').forEach(function(kol) {
        if (parseInt(kol.dataset.dagidx, 10) === iDagIdx) kol.classList.add('idag');
    });

    // Mobil-tabs: dato som topplabel
    document.querySelectorAll('.dag-tab-navn[data-dagidx]').forEach(function(el) {
        var idx = parseInt(el.dataset.dagidx, 10);
        var dato = new Date(mandag);
        dato.setDate(mandag.getDate() + idx);
        el.textContent = dato.getDate() + '. ' + MND_KORT[dato.getMonth()];
    });

    // Ukenummer + print-info
    var iso = isoUkeNr(iDag);
    var ukeEl = document.getElementById('ukeNummer');
    if (ukeEl) ukeEl.textContent = 'Uke ' + iso.uke;
    var printEl = document.getElementById('printUkeInfo');
    if (printEl) {
        var sondag = new Date(mandag);
        sondag.setDate(mandag.getDate() + 6);
        printEl.textContent = 'Uke ' + iso.uke + ' — ' +
            mandag.getDate() + '. ' + MND_KORT[mandag.getMonth()] + ' til ' +
            sondag.getDate() + '. ' + MND_KORT[sondag.getMonth()] + ' ' + sondag.getFullYear();
    }

    // Start på dagens dag (mobil)
    byttDagIdx(iDagIdx);
}

/* ── LEGG TIL-MODAL (fra uke-grid) ──────────────────────── */
function apneLeggTil(slot) {
    var dag = slot.dataset.dag;
    var maltid = slot.dataset.maltid;
    document.getElementById('ltDag').value = dag;
    document.getElementById('ltMaltid').value = maltid;
    document.getElementById('ltInput').value = '';
    document.getElementById('leggTilTittel').textContent = 'Legg til ' + maltid.toLowerCase() + ' — ' + dag;
    document.getElementById('leggTilModal').classList.add('vis');
    document.body.style.overflow = 'hidden';
    setTimeout(function() { document.getElementById('ltInput').focus(); }, 60);
}
function lukkLeggTil() {
    document.getElementById('leggTilModal').classList.remove('vis');
    document.body.style.overflow = '';
}
function lukkLeggTilHvis(e) {
    if (e.target === document.getElementById('leggTilModal')) lukkLeggTil();
}

/* ── DRA OG SLIPP (HTML5 native) ────────────────────────── */
function initDragDrop() {
    document.querySelectorAll('.uge-slot').forEach(function(slot) {
        if (slot.classList.contains('fylt')) {
            slot.addEventListener('dragstart', function(e) {
                e.dataTransfer.setData('text/plain', slot.dataset.id);
                e.dataTransfer.effectAllowed = 'move';
                slot.classList.add('dragging');
            });
            slot.addEventListener('dragend', function() {
                slot.classList.remove('dragging');
                document.querySelectorAll('.uge-slot.drop-hover').forEach(function(s) {
                    s.classList.remove('drop-hover');
                });
            });
        }
        slot.addEventListener('dragover', function(e) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            slot.classList.add('drop-hover');
        });
        slot.addEventListener('dragleave', function() { slot.classList.remove('drop-hover'); });
        slot.addEventListener('drop', function(e) {
            e.preventDefault();
            slot.classList.remove('drop-hover');
            var id = e.dataTransfer.getData('text/plain');
            if (!id || slot.dataset.id === id) return;
            flyttMaltid(id, slot.dataset.dag, slot.dataset.maltid);
        });
    });
}

function flyttMaltid(id, dag, maltid) {
    var form = document.createElement('form');
    form.method = 'POST';
    form.action = '/ukesmeny/flytt/' + id;
    [['dag', dag], ['maltid', maltid]].forEach(function(f) {
        var inp = document.createElement('input');
        inp.type = 'hidden'; inp.name = f[0]; inp.value = f[1];
        form.appendChild(inp);
    });
    var csrf = document.querySelector('meta[name="_csrf"]');
    if (csrf) {
        var ci = document.createElement('input');
        ci.type = 'hidden'; ci.name = '_csrf'; ci.value = csrf.content;
        form.appendChild(ci);
    }
    document.body.appendChild(form);
    form.submit();
}

/* ── KOPIER FORRIGE UKES MENY (localStorage-snapshot) ───── */
function ukeSnapshotKey(dato) {
    var iso = isoUkeNr(dato);
    return 'mm-ukesmeny-' + iso.aar + '-W' + iso.uke;
}

function hentGjeldendeMeny() {
    return Array.from(document.querySelectorAll('.uge-slot.fylt')).map(function(s) {
        return { dag: s.dataset.dag, maltid: s.dataset.maltid, tittel: s.dataset.tittel };
    });
}

function lagreUkeSnapshot() {
    try {
        var meny = hentGjeldendeMeny();
        if (meny.length > 0) {
            localStorage.setItem(ukeSnapshotKey(new Date()), JSON.stringify(meny));
        }
    } catch (e) { /* localStorage utilgjengelig — ignorer */ }
}

async function kopierForrigeUke() {
    var forrige = new Date();
    forrige.setDate(forrige.getDate() - 7);
    var lagret = null;
    try { lagret = localStorage.getItem(ukeSnapshotKey(forrige)); } catch (e) {}
    if (!lagret) {
        if (typeof showToast === 'function') showToast('Fant ingen lagret meny fra forrige uke', 'info');
        else alert('Fant ingen lagret meny fra forrige uke.');
        return;
    }
    var meny;
    try { meny = JSON.parse(lagret); } catch (e) { return; }
    if (!meny || !meny.length) return;
    if (!confirm('Legge inn ' + meny.length + ' måltider fra forrige ukes meny? Eksisterende måltider i samme slot blir erstattet.')) return;

    var btn = document.getElementById('kopierUkeBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Kopierer...';
    }
    for (var i = 0; i < meny.length; i++) {
        var m = meny[i];
        await csrfPost('/ukesmeny/legg-til',
            'dag=' + encodeURIComponent(m.dag) +
            '&maltid=' + encodeURIComponent(m.maltid) +
            '&tittel=' + encodeURIComponent(m.tittel),
            'application/x-www-form-urlencoded');
    }
    window.location.reload();
}

document.addEventListener('DOMContentLoaded', function() {
    initUkeDatoer();
    initDragDrop();
    lagreUkeSnapshot();
});

var aktivTittel = '';
var aktivPorsjoner = 2;

function apneModal(tittel) {
    aktivTittel = tittel;
    aktivPorsjoner = 2;
    document.getElementById('modalTittel').textContent = tittel;
    document.getElementById('porsjonTall').textContent = 2;
    document.getElementById('modalResultat').innerHTML = '';
    document.getElementById('oppskriftModal').classList.add('vis');
    document.body.style.overflow = 'hidden';
}

function lukkModal() {
    document.getElementById('oppskriftModal').classList.remove('vis');
    document.body.style.overflow = '';
}

function lukkModalHvis(e) {
    if (e.target === document.getElementById('oppskriftModal')) lukkModal();
}

function endrePorsjoner(delta) {
    aktivPorsjoner = Math.max(1, Math.min(20, aktivPorsjoner + delta));
    document.getElementById('porsjonTall').textContent = aktivPorsjoner;
    document.getElementById('modalResultat').innerHTML = '';
}

function getCsrf() {
    var meta = document.querySelector('meta[name="_csrf"]');
    return meta ? meta.content : '';
}
function getCsrfHeader() {
    var meta = document.querySelector('meta[name="_csrf_header"]');
    return meta ? meta.content : 'X-CSRF-TOKEN';
}
function csrfPost(url, body, contentType) {
    var headers = { 'Content-Type': contentType || 'application/json' };
    headers[getCsrfHeader()] = getCsrf();
    return fetch(url, { method: 'POST', headers: headers, body: body });
}

async function hentOppskrift() {
    var btn = document.getElementById('genererBtn');
    btn.disabled = true;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Genererer...';
    document.getElementById('modalResultat').innerHTML =
        '<div class="modal-loading"><i class="fa-solid fa-spinner fa-spin" style="font-size:1.5rem;color:#22c55e;"></i><br><br>Claude lager oppskrift...</div>';

    try {
        var res = await csrfPost('/api/ukesmeny/oppskrift',
            JSON.stringify({ tittel: aktivTittel, porsjoner: String(aktivPorsjoner) }),
            'application/json');
        var data = await res.json();

        if (data.feil) {
            document.getElementById('modalResultat').innerHTML =
                '<div class="modal-feil">Feil: ' + data.feil + '</div>';
        } else {
            var porsjonTekst = 'Ingredienser \u2014 ' + aktivPorsjoner + ' porsjon' + (aktivPorsjoner !== 1 ? 'er' : '');
            document.getElementById('modalResultat').innerHTML =
                '<div class="modal-seksjon">' +
                    '<div class="modal-seksjon-tittel">' + porsjonTekst + '</div>' +
                    '<div class="modal-tekst">' + data.ingredienser + '</div>' +
                '</div>' +
                '<div class="modal-seksjon">' +
                    '<div class="modal-seksjon-tittel">Fremgangsm\u00E5te</div>' +
                    '<div class="modal-tekst">' + data.fremgangsmate + '</div>' +
                '</div>' +
                '<div class="lagre-rad">' +
                    '<button class="lagre-btn" onclick="lagreOppskrift()">' +
                        '<i class="fa-solid fa-plus"></i> Lagre i kokebok' +
                    '</button>' +
                    '<button class="lagre-btn" onclick="leggTilHandelListe()">' +
                        '<i class="fa-solid fa-cart-shopping"></i> Til handleliste' +
                    '</button>' +
                '</div>';
        }
    } catch (e) {
        document.getElementById('modalResultat').innerHTML =
            '<div class="modal-feil">Kunne ikke kontakte serveren. Pr\u00F8v igjen.</div>';
    }

    btn.disabled = false;
    btn.innerHTML = '<i class="fa-solid fa-wand-magic-sparkles"></i> Generer p\u00E5 nytt';
}

function lagreOppskrift() {
    var ing = document.querySelector('.modal-seksjon:nth-child(1) .modal-tekst');
    var frem = document.querySelector('.modal-seksjon:nth-child(2) .modal-tekst');
    var ingTekst = ing ? ing.textContent : '';
    var fremTekst = frem ? frem.textContent : '';
    var form = document.createElement('form');
    form.method = 'POST';
    form.action = '/lagre';
    var csrf = document.querySelector('meta[name="_csrf"]');
    if (csrf) {
        var ci = document.createElement('input');
        ci.type = 'hidden'; ci.name = '_csrf'; ci.value = csrf.content;
        form.appendChild(ci);
    }
    var felt = [['tittel', aktivTittel], ['ingredienser', ingTekst], ['fremgangsmate', fremTekst],
                ['porsjoner', aktivPorsjoner], ['erOffentlig', 'false']];
    felt.forEach(function(f) {
        var inp = document.createElement('input');
        inp.type = 'hidden'; inp.name = f[0]; inp.value = f[1];
        form.appendChild(inp);
    });
    document.body.appendChild(form);
    form.submit();
}

async function leggTilHandelListe() {
    var ing = document.querySelector('.modal-seksjon:nth-child(1) .modal-tekst');
    var tekst = ing ? ing.textContent : '';
    var linjer = tekst.split('\n').filter(function(l) { return l.trim().startsWith('-'); });
    for (var i = 0; i < linjer.length; i++) {
        await csrfPost('/handleliste/legg-til',
            'tekst=' + encodeURIComponent(linjer[i].replace(/^-\s*/, '').trim()),
            'application/x-www-form-urlencoded');
    }
    lukkModal();
    window.location.href = '/handleliste';
}

document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        lukkModal();
        if (typeof lukkLeggTil === 'function') lukkLeggTil();
    }
});
