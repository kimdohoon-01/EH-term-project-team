const message = document.querySelector("#message");
const won = new Intl.NumberFormat("ko-KR");

function report(text, error = false) {
    message.textContent = text;
    message.dataset.error = error;
    message.hidden = false;
}

async function api(path, method = "GET", data) {
    const response = await fetch(path, {
        method,
        headers: data ? {"Content-Type": "application/json"} : undefined,
        body: data ? JSON.stringify(data) : undefined,
        cache: "no-store"
    });
    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.detail || result.title || "요청을 처리할 수 없습니다.");
    }
    return result;
}

async function run(button, action) {
    button.disabled = true;
    try {
        await action();
    } catch (error) {
        report(error.message, true);
    } finally {
        button.disabled = false;
    }
}

function rowWith(values) {
    const row = document.createElement("tr");
    for (const value of values) {
        row.insertCell().textContent = value;
    }
    return row;
}

function emptyRow(text, columns) {
    const row = rowWith([text]);
    row.firstElementChild.colSpan = columns;
    return row;
}

const menuForm = document.querySelector("#menu-form");
const adjustForm = document.querySelector("#adjust-form");
const stockForm = document.querySelector("#stock-form");
const menus = document.querySelector("#menus");
const reservation = document.querySelector("#reservation");
let editingId = null;

function editMenu(menu) {
    editingId = menu?.id ?? null;
    menuForm.reset();
    document.querySelector("#editor-title").textContent = menu ? "메뉴 수정 · " + menu.id : "메뉴 등록";
    document.querySelector("#active-control").hidden = !menu;
    document.querySelector("#initial-stock").hidden = Boolean(menu);
    menuForm.elements.stock.disabled = Boolean(menu);
    if (menu) {
        menuForm.elements.name.value = menu.name;
        menuForm.elements.price.value = menu.price;
        menuForm.elements.active.value = String(menu.active);
        adjustForm.elements.menuId.value = menu.id;
        stockForm.elements.menuId.value = menu.id;
    }
}

async function refreshMenus() {
    const items = await api("/api/menus?includeInactive=true");
    const rows = items.map(menu => {
        const row = rowWith([menu.id, menu.name, won.format(menu.price) + "원", menu.stock,
            menu.active ? "판매 중" : "판매 중지"]);
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "수정";
        edit.setAttribute("aria-label", menu.name + " 수정");
        edit.addEventListener("click", () => {
            editMenu(menu);
            menuForm.elements.name.focus();
        });
        row.insertCell().append(edit);
        return row;
    });
    menus.replaceChildren(...(rows.length ? rows : [emptyRow("등록된 메뉴가 없습니다.", 6)]));
}

document.querySelector("#new-menu").addEventListener("click", () => {
    editMenu();
    menuForm.elements.name.focus();
});

menuForm.addEventListener("submit", event => {
    event.preventDefault();
    const data = new FormData(menuForm);
    const id = editingId;
    run(event.submitter, async () => {
        const menu = {name: data.get("name"), price: Number(data.get("price"))};
        if (id === null) {
            await api("/api/menus", "POST", {...menu, stock: Number(data.get("stock"))});
            editMenu();
        } else {
            await api("/api/menus/" + id, "PUT", {...menu, active: data.get("active") === "true"});
        }
        await refreshMenus();
        report("메뉴를 저장했습니다.");
    });
});

adjustForm.addEventListener("submit", event => {
    event.preventDefault();
    const data = new FormData(adjustForm);
    run(event.submitter, async () => {
        const item = await api("/api/menus/" + data.get("menuId") + "/stock-adjustments", "POST",
            {delta: Number(data.get("delta"))});
        await refreshMenus();
        report(item.name + " 재고: " + item.stock);
    });
});

stockForm.addEventListener("submit", event => {
    event.preventDefault();
    if (!stockForm.elements.orderId.reportValidity()) return;
    const data = new FormData(stockForm);
    const path = "/api/stock/reservations/" + encodeURIComponent(data.get("orderId"));
    const action = event.submitter.value;
    reservation.hidden = true;
    run(event.submitter, async () => {
        let result;
        if (action === "reserve") {
            const items = [{menuId: Number(data.get("menuId")), quantity: Number(data.get("quantity"))}];
            result = await api(path, "PUT", {items});
        } else if (action === "release") {
            result = await api(path + "/release", "POST");
        } else {
            result = await api(path);
        }
        const state = result.status === "RELEASED" ? "재고 복구 완료" : "재고 차감 완료";
        reservation.textContent = result.orderId + " · " + state + " · 결제 금액 " + won.format(result.totalAmount) + "원";
        reservation.hidden = false;
        await refreshMenus();
        report("재고 기록을 확인했습니다.");
    });
});

refreshMenus().catch(error => report(error.message, true));
