'use strict';
const $ = id => document.getElementById(id);
const state = {menus:[],cart:new Map(),category:'ALL',tab:'menu',user:null,token:sessionStorage.getItem('og-token'),register:false,busy:false,testPayments:false};
const money = value => Number(value).toLocaleString('ko-KR');
const date = value => new Date(value).toLocaleString('ko-KR',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'});
const node = (tag,cls,text) => {const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;};
const action = (text,cls,fn) => {const b=node('button',cls,text);b.type='button';b.addEventListener('click',fn);return b;};
let toastTimer;
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,4000);}
async function api(path,options={}) {
  const response=await fetch('/shop/api'+path,{cache:'no-store',...options,headers:{'Content-Type':'application/json',...(state.token?{Authorization:'Bearer '+state.token}:{}),...options.headers}});
  const body=await response.json().catch(()=>({message:'응답을 읽을 수 없습니다.'}));
  if(!response.ok){const error=new Error(body.message||'요청을 처리할 수 없습니다.');error.status=response.status;throw error;}
  return body;
}
function art(menu){
  const box=node('div','menu-art');box.dataset.id=menu.id;
  // 고정된 SVG만 HTML로 넣고, 외부에서 받은 메뉴명은 textContent로 표시한다.
  box.innerHTML=menu.category==='DESSERT'?'<svg viewBox="0 0 120 120" aria-hidden="true"><ellipse cx="61" cy="99" rx="37" ry="6" fill="#6d4b2c" opacity=".1"/><circle cx="60" cy="60" r="38" fill="currentColor"/><circle cx="57" cy="57" r="33" fill="#d1a775"/><g fill="#634b39"><rect x="40" y="38" width="10" height="9" rx="2" transform="rotate(20 45 42)"/><rect x="64" y="30" width="8" height="10" rx="2" transform="rotate(-17 68 35)"/><rect x="69" y="54" width="11" height="8" rx="2"/><rect x="41" y="64" width="9" height="10" rx="2" transform="rotate(-20 45 70)"/><rect x="62" y="79" width="9" height="8" rx="2"/></g></svg>':'<svg viewBox="0 0 120 120" aria-hidden="true"><ellipse cx="60" cy="106" rx="29" ry="5" fill="#655944" opacity=".1"/><path d="M31 26h58l-7 72q-22 10-44 0z" fill="currentColor"/><path d="M34 41h52l-4 44H38z" fill="#f6f1e1" opacity=".9"/><path d="M29 24h62v9H29z" fill="#f8f6ec"/><ellipse cx="60" cy="23" rx="32" ry="6" fill="#e4e0d0"/><ellipse cx="60" cy="22" rx="25" ry="3" fill="#6a5140"/><path d="M66 22l8-18" stroke="#b1b898" stroke-width="4"/><circle cx="60" cy="64" r="13" fill="#375640"/><text x="59" y="70" font-family="Georgia,serif" font-size="19" text-anchor="middle" fill="#f1efd4">o.</text></svg>';
  box.append(node('span','stock-tag',menu.stock>0?'남은 수량 '+menu.stock:'SOLD OUT'));return box;
}
let menuRequest = 0;
let menuSnapshot = null;
async function loadMenus() {
    const request = ++menuRequest;
    const menus = await api('/menus');
    if (request !== menuRequest) return;

    let removed = false;
    if (!pending()) {
        const available = new Set(menus.map(menu => menu.id));
        for (const id of state.cart.keys()) {
            if (!available.has(id)) {
                state.cart.delete(id);
                removed = true;
            }
        }
    }

    const snapshot = JSON.stringify(menus);
    if (snapshot !== menuSnapshot) {
        menuSnapshot = snapshot;
        state.menus = menus;
        renderMenus();
        renderCart();
    } else if (removed) {
        renderCart();
    }
    if (removed) toast('판매가 종료된 메뉴를 장바구니에서 뺐어요.');
}
function locked(){return state.busy||Boolean(pending());}
function renderMenus(){
  const root=$('menu-list');root.replaceChildren();
  for(const menu of state.menus.filter(m=>state.category==='ALL'||m.category===state.category)){
    const card=node('article','menu-card');card.append(art(menu));const info=node('div','menu-info');info.append(node('h3','',menu.name),node('p','',menu.description));
    const bottom=node('div','menu-bottom');bottom.append(node('span','price',money(menu.price)+'원'));
    const add=action('+','add',()=>changeCart(menu.id,1));add.setAttribute('aria-label',menu.name+' 담기');add.disabled=menu.stock===0||locked();bottom.append(add);info.append(bottom);card.append(info);root.append(card);
  }
  if(!root.childElementCount)root.append(node('p','empty','이 분류의 메뉴가 아직 없어요.'));
}
function pending(){if(!state.user)return null;try{return JSON.parse(sessionStorage.getItem('og-pending-'+state.user.id)||'null');}catch{return null;}}
function setPending(value){const key='og-pending-'+state.user.id;if(value)sessionStorage.setItem(key,JSON.stringify(value));else sessionStorage.removeItem(key);}
function changeCart(id,delta){
  if(locked())return;const menu=state.menus.find(m=>m.id===id);const q=(state.cart.get(id)||0)+delta;
  if(q>Math.min(menu.stock,20)){toast('주문 가능한 수량을 확인해 주세요.');return;}
  if(q<=0)state.cart.delete(id);else state.cart.set(id,q);renderCart();
}
function renderCart(){
  const root=$('cart-items');root.replaceChildren();let total=0,count=0;
  for(const [id,q]of state.cart){
    const menu=state.menus.find(m=>m.id===id);if(!menu)continue;total+=menu.price*q;count+=q;
    const item=node('div','cart-item'),top=node('div','cart-item-top'),bottom=node('div','cart-item-bottom'),quantity=node('div','quantity');
    top.append(node('strong','',menu.name));const remove=action('×','remove',()=>{state.cart.delete(id);renderCart();});remove.setAttribute('aria-label',menu.name+' 삭제');remove.disabled=locked();top.append(remove);
    const minus=action('−','',()=>changeCart(id,-1)),plus=action('+','',()=>changeCart(id,1));minus.setAttribute('aria-label',menu.name+' 수량 줄이기');plus.setAttribute('aria-label',menu.name+' 수량 늘리기');minus.disabled=plus.disabled=locked();quantity.append(minus,node('span','',q),plus);bottom.append(quantity,node('span','cart-item-price',money(menu.price*q)+'원'));item.append(top,bottom);root.append(item);
  }
  if(!count){const empty=node('div','empty-cart');empty.append(node('span','','♧'),document.createTextNode('아직 담긴 메뉴가 없어요.'));root.append(empty);}
  $('cart-count').textContent=count;$('cart-total').replaceChildren(document.createTextNode(money(total)),node('span','','원'));
  $('checkout').disabled=state.busy||(!count&&!pending());$('checkout').textContent=state.busy?'주문을 처리하고 있어요…':pending()?'이전 주문 결과 다시 확인':!count?'메뉴를 담아주세요':!state.user?'로그인하고 주문하기':money(total)+'원 주문하기';
  $('decline-payment').disabled=locked();
  if(pending())$('checkout-message').textContent='이전 요청의 결과를 확인해 주세요. 같은 주문이 중복 생성되지 않도록 다시 확인합니다.';
}
async function checkout(){
  if(!state.user){openAuth();return;}if(state.busy)return;
  const request=pending()||{key:crypto.randomUUID(),body:{items:[...state.cart].map(([menuId,quantity])=>({menuId,quantity})),paymentMethod:$('decline-payment').checked?'TEST_DECLINE':'CARD'}};
  if(!request.body.items.length)return;setPending(request);state.busy=true;renderMenus();renderCart();$('checkout-message').textContent='재고 확인 후 결제를 진행하고 있어요.';
  try{
    const order=await api('/orders',{method:'POST',headers:{'Idempotency-Key':request.key},body:JSON.stringify(request.body)});
    setPending(null);if(order.status!=='CANCELLED')state.cart.clear();$('checkout-message').textContent=order.message;toast(order.message);if(order.status!=='CANCELLED')showTab('orders');
    await loadMenus();await loadPrivate();
  }catch(error){
    if(error.status && error.status<500)setPending(null);
    $('checkout-message').textContent=error.message||'연결이 끊겼어요. 같은 주문 결과를 다시 확인해 주세요.';toast($('checkout-message').textContent);
  }finally{state.busy=false;renderMenus();renderCart();}
}
function showTab(tab){state.tab=tab;for(const name of ['menu','orders','notifications'])$(name+'-panel').hidden=name!==tab;$('hero').hidden=tab!=='menu';document.querySelectorAll('[data-tab]').forEach(b=>b.classList.toggle('active',b.dataset.tab===tab));if(tab!=='menu')loadPrivate().catch(e=>toast(e.message));}
const statusNames={CREATED:'주문 접수 중',STOCK_PENDING:'재고 확인 중',STOCK_RESERVED:'재고 확보',PAYMENT_PENDING:'결제 확인 중',CONFIRMED:'주문 확정',CANCEL_PENDING:'재고 복구 중',CANCELLED:'주문 취소',LEGACY_REVIEW:'이전 주문 확인 필요'};
function renderOrders(orders){
  const root=$('orders-list');root.replaceChildren();
  if(!orders.length){root.append(node('p','empty','아직 주문 내역이 없어요. 오늘의 첫 주문을 해보세요.'));return;}
  for(const order of orders){
    const card=node('article','history-card'),top=node('div','history-top');top.append(node('time','',date(order.createdAt)),node('span','status '+(order.status==='CANCELLED'?'cancelled':order.status==='CONFIRMED'?'':'pending'),statusNames[order.status]||order.status));
    card.append(top,node('h3','',order.items.map(i=>(i.name||i.menuId)+' × '+i.quantity).join(', ')),node('p','',order.message),node('p','order-total',money(order.total)+'원'),node('div','history-meta','주문번호 '+order.id.slice(0,8)));root.append(card);
  }
}
function renderNotifications(notifications){
  const root=$('notification-list');root.replaceChildren();const unread=notifications.filter(n=>!n.read).length;$('unread').textContent=unread;$('unread').hidden=!unread;
  if(!notifications.length){root.append(node('p','empty','주문 결과가 도착하면 여기에서 알려드릴게요.'));return;}
  for(const n of notifications){const card=node('article','history-card notification-card'+(n.read?'':' unread'));card.append(node('time','',date(n.createdAt)),node('h3','',n.title),node('p','',n.message),node('div','history-meta','주문번호 '+n.orderId.slice(0,8)));if(!n.read)card.append(action('읽음으로 표시','button outline',async()=>{try{await api('/notifications/'+encodeURIComponent(n.id)+'/read',{method:'PATCH'});await loadPrivate();}catch(e){toast(e.message);}}));root.append(card);}
}
async function loadPrivate(){
  if(!state.user){for(const id of ['orders-list','notification-list']){const empty=node('div','empty');empty.append(node('p','','로그인하면 확인할 수 있어요.'),action('로그인하기','button outline',openAuth));$(id).replaceChildren(empty);}$('unread').hidden=true;return;}
  const [orders,notifications]=await Promise.allSettled([api('/orders'),api('/notifications')]);
  if(orders.status==='fulfilled')renderOrders(orders.value);else if(orders.reason.status===401){forgetLogin();toast('로그인이 만료되었어요. 다시 로그인해 주세요.');return;}
  else if(state.tab==='orders')$('orders-list').replaceChildren(node('p','empty',orders.reason.message));
  if(notifications.status==='fulfilled')renderNotifications(notifications.value);else if(state.tab==='notifications')$('notification-list').replaceChildren(node('p','empty',notifications.reason.message));
}
function openAuth(){if(!$('auth-dialog').open)$('auth-dialog').showModal();$('auth-error').textContent='';}
function updateAccount(){$('account-button').textContent=state.user?state.user.name+'님 · 로그아웃':'로그인 / 회원가입';}
function forgetLogin(){state.token=null;state.user=null;sessionStorage.removeItem('og-token');updateAccount();renderMenus();renderCart();loadPrivate();}
function switchAuth(){state.register=!state.register;
  for(const id of ['address-field','address-detail-field','confirm-field','password-help'])$(id).hidden=!state.register;
  $('auth-address').required=state.register;$('auth-password-confirm').required=state.register;
  $('auth-address').disabled=!state.register;$('auth-address-detail').disabled=!state.register;$('auth-password-confirm').disabled=!state.register;
  $('auth-password-confirm').value='';
  $('name-field').hidden=!state.register;$('auth-name').required=state.register;$('auth-password').minLength=state.register?8:1;$('auth-password').autocomplete=state.register?'new-password':'current-password';$('auth-title').textContent=state.register?'여유로운 하루의 시작':'다시 만나 반가워요';$('auth-subtitle').textContent=state.register?'오더앤고와 함께 첫 주문을 시작해 보세요.':'로그인하고 나만의 한 잔을 주문해 보세요.';$('auth-submit').textContent=state.register?'회원가입':'로그인';$('auth-switch-label').textContent=state.register?'이미 계정이 있나요?':'아직 계정이 없나요?';$('switch-auth').textContent=state.register?'로그인':'회원가입';$('auth-error').textContent='';}
$('auth-form').addEventListener('submit',async event=>{
  event.preventDefault();$('auth-submit').disabled=true;$('auth-error').textContent='';
  try{const body={email:$('auth-email').value.trim(),password:$('auth-password').value};if(state.register){
      if(!/^[!-~]{8,72}$/.test(body.password)||!/[A-Za-z]/.test(body.password)||!/[0-9]/.test(body.password)||!/[^A-Za-z0-9]/.test(body.password))throw new Error('비밀번호는 공백 없이 영문·숫자·특수문자를 포함한 8~72자로 입력해 주세요.');
      if(body.password!==$('auth-password-confirm').value)throw new Error('비밀번호 확인이 일치하지 않습니다.');
      body.name=$('auth-name').value.trim();body.address=$('auth-address').value.trim();body.addressDetail=$('auth-address-detail').value.trim();
      if(body.address.length<5)throw new Error('기본주소를 5자 이상 입력해 주세요.');
    }const result=await api('/auth/'+(state.register?'register':'login'),{method:'POST',body:JSON.stringify(body)});state.token=result.accessToken;state.user=result.user;sessionStorage.setItem('og-token',state.token);$('auth-dialog').close();$('auth-password').value='';$('auth-password-confirm').value='';$('auth-address').value='';$('auth-address-detail').value='';updateAccount();renderMenus();renderCart();await loadPrivate();toast(state.user.name+'님, 반가워요!');}
  catch(error){$('auth-error').textContent=error.message;}finally{$('auth-submit').disabled=false;}
});
$('account-button').addEventListener('click',async()=>{if(!state.user){openAuth();return;}try{await api('/auth/session',{method:'DELETE'});forgetLogin();toast('로그아웃했어요.');}catch(e){toast(e.message);}});
$('close-auth').addEventListener('click',()=>$('auth-dialog').close());$('switch-auth').addEventListener('click',switchAuth);$('checkout').addEventListener('click',checkout);
document.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>showTab(b.dataset.tab)));
document.querySelectorAll('[data-category]').forEach(b=>b.addEventListener('click',()=>{state.category=b.dataset.category;document.querySelectorAll('[data-category]').forEach(c=>c.classList.toggle('active',c===b));renderMenus();}));
document.querySelectorAll('.refresh').forEach(b=>b.addEventListener('click',()=>loadPrivate().catch(e=>toast(e.message))));
function refreshVisibleMenus() {
    if (!document.hidden && !state.busy) loadMenus().catch(() => {});
}
document.addEventListener('visibilitychange', refreshVisibleMenus);
const menuEvents = typeof EventSource === 'function' ? new EventSource('/api/menus/events') : null;
function refreshFromMenuEvent() {
    if (!state.busy) loadMenus().catch(() => {});
}
menuEvents?.addEventListener('open', refreshFromMenuEvent);
menuEvents?.addEventListener('menu-changed', refreshFromMenuEvent);
async function init(){
  try{const config=await api('/config');state.testPayments=config.testPayments;$('test-panel').hidden=!config.testPayments;}catch{}
  if(state.token){try{state.user=await api('/auth/me');}catch(e){if(e.status===401)forgetLogin();else toast(e.message);}}
  updateAccount();try{await loadMenus();}catch(e){$('menu-list').replaceChildren(node('p','empty',e.message));}renderCart();await loadPrivate();
  setInterval(()=>{
    if (!menuEvents || menuEvents.readyState !== EventSource.OPEN) refreshVisibleMenus();
    if(!document.hidden&&state.user)loadPrivate().catch(()=>{});
  },5000);
}
init().catch(e=>toast(e.message));
