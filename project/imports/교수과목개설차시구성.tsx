import svgPaths from "./svg-2doawf4qog";
import clsx from "clsx";
import img2 from "figma:asset/064ff24825faacd0d8397924e31fc7ca40825c52.png";
type Icon1Props = {
  additionalClassNames?: string;
};

function Icon1({ children, additionalClassNames = "" }: React.PropsWithChildren<Icon1Props>) {
  return (
    <div className={clsx("absolute left-0 overflow-clip", additionalClassNames)}>
      <div className="absolute flex inset-[6.25%_18%] items-center justify-center">{children}</div>
    </div>
  );
}
type Wrapper3Props = {
  additionalClassNames?: string;
};

function Wrapper3({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper3Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium justify-center leading-[0] relative shrink-0 text-[16px] text-nowrap", additionalClassNames)}>
      <p className="leading-[24px]">{children}</p>
    </div>
  );
}
type Wrapper2Props = {
  additionalClassNames?: string;
};

function Wrapper2({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper2Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Regular','Noto_Sans_KR:Regular',sans-serif] font-normal justify-center leading-[0] relative shrink-0 text-[16px] text-nowrap", additionalClassNames)}>
      <p className="leading-[24px]">{children}</p>
    </div>
  );
}
type Wrapper1Props = {
  additionalClassNames?: string;
};

function Wrapper1({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper1Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:SemiBold','Noto_Sans_KR:Bold',sans-serif] font-semibold justify-center leading-[0] relative shrink-0 text-[18px]", additionalClassNames)}>
      <p className="leading-[28px]">{children}</p>
    </div>
  );
}
type WrapperProps = {
  additionalClassNames?: string;
};

function Wrapper({ children, additionalClassNames = "" }: React.PropsWithChildren<WrapperProps>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium justify-center leading-[0] relative shrink-0 text-[14px] text-nowrap", additionalClassNames)}>
      <p className="leading-[20px]">{children}</p>
    </div>
  );
}

function Group({ children }: React.PropsWithChildren<{}>) {
  return (
    <div className="relative size-full">
      <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 14 14">
        <g id="Group">{children}</g>
      </svg>
    </div>
  );
}
type LabelTextProps = {
  text: string;
};

function LabelText({ text }: LabelTextProps) {
  return (
    <div className="content-stretch flex h-[20px] items-center relative shrink-0 w-[1006px]">
      <Wrapper additionalClassNames="text-[#374151]">{text}</Wrapper>
    </div>
  );
}
type DivTextProps = {
  text: string;
  additionalClassNames?: string;
};

function DivText({ text, additionalClassNames = "" }: DivTextProps) {
  return (
    <div className={clsx("content-stretch flex h-[20px] items-center relative shrink-0", additionalClassNames)}>
      <Wrapper additionalClassNames="text-[#2563eb]">{text}</Wrapper>
    </div>
  );
}

function Icon() {
  return (
    <div className="overflow-clip relative shrink-0 size-[16px]">
      <div className="absolute flex inset-[10.42%_14%] items-center justify-center">
        <div className="flex-none h-[12.667px] scale-y-[-100%] w-[11.52px]">
          <div className="relative size-full">
            <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 12 13">
              <g id="Group">
                <path d={svgPaths.p37e59e80} fill="var(--fill-0, #4B5563)" id="Vector" />
              </g>
            </svg>
          </div>
        </div>
      </div>
    </div>
  );
}
type HTextProps = {
  text: string;
  additionalClassNames?: string;
};

function HText({ text, additionalClassNames = "" }: HTextProps) {
  return (
    <div className={clsx("content-stretch flex h-[28px] items-center relative shrink-0", additionalClassNames)}>
      <Wrapper1 additionalClassNames="text-[#111827] text-nowrap">{text}</Wrapper1>
    </div>
  );
}

export default function Component() {
  return (
    <div className="bg-white relative size-full" data-name="교수 과목개설 차시구성">
      <div className="absolute content-stretch flex h-[1024px] items-start left-0 top-[73px] w-[1440px]" data-name="DIV-31">
        <div className="bg-white content-stretch flex flex-col h-[1024px] items-start relative shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] shrink-0 w-[256px]" data-name="DIV-32">
          <div className="content-stretch flex flex-col h-[239px] items-start p-[24px] relative shrink-0 w-[256px]" data-name="DIV-33">
            <div className="content-stretch flex items-start pb-[24px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
              <HText text="교수자 관리" additionalClassNames="w-[208px]" />
            </div>
            <div className="content-start flex flex-wrap gap-0 h-[139px] items-start relative shrink-0 w-[208px]" data-name="NAV-37">
              <div className="content-stretch flex h-[41px] items-start px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[208px]" data-name="BUTTON-38">
                <div className="content-stretch flex items-start pb-0 pl-0 pr-[12px] pt-[4px] relative shrink-0" data-name="margin-wrap">
                  <div className="overflow-clip relative shrink-0 size-[16px]" data-name="Icon-39">
                    <div className="absolute flex inset-[8.33%_14%] items-center justify-center">
                      <div className="flex-none h-[13.333px] scale-y-[-100%] w-[11.52px]">
                        <div className="relative size-full" data-name="Group">
                          <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 12 14">
                            <g id="Group">
                              <path d={svgPaths.p366cbc80} fill="var(--fill-0, #4B5563)" id="Vector" />
                            </g>
                          </svg>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <Wrapper2 additionalClassNames="text-[#4b5563]">과목 관리</Wrapper2>
              </div>
              <div className="content-stretch flex items-start pb-0 pt-[8px] px-0 relative shrink-0" data-name="margin-wrap">
                <div className="bg-[#dbeafe] content-stretch flex h-[41px] items-start px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[208px]" data-name="BUTTON-42">
                  <div className="content-stretch flex items-start pb-0 pl-0 pr-[12px] pt-[4px] relative shrink-0" data-name="margin-wrap">
                    <div className="overflow-clip relative shrink-0 size-[16px]" data-name="Icon-43">
                      <div className="absolute flex inset-[20.83%_22%] items-center justify-center">
                        <div className="flex-none h-[9.333px] scale-y-[-100%] w-[8.96px]">
                          <div className="relative size-full" data-name="Group">
                            <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 9 10">
                              <g id="Group">
                                <path d={svgPaths.p6e0d900} fill="var(--fill-0, #1D4ED8)" id="Vector" />
                              </g>
                            </svg>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <Wrapper2 additionalClassNames="text-[#1d4ed8]">과목 개설</Wrapper2>
                </div>
              </div>
              <div className="content-stretch flex items-start pb-0 pt-[8px] px-0 relative shrink-0" data-name="margin-wrap">
                <div className="content-stretch flex h-[41px] items-start px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[208px]" data-name="BUTTON-46">
                  <div className="content-stretch flex items-start pb-0 pl-0 pr-[12px] pt-[4px] relative shrink-0" data-name="margin-wrap">
                    <Icon />
                  </div>
                  <Wrapper2 additionalClassNames="text-[#4b5563]">콘텐츠 라이브러리</Wrapper2>
                </div>
              </div>
              <div className="content-stretch flex items-start pb-0 pt-[8px] px-0 relative shrink-0" data-name="margin-wrap">
                <div className="content-stretch flex h-[41px] items-start px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[208px]" data-name="BUTTON-46">
                  <div className="content-stretch flex items-start pb-0 pl-0 pr-[12px] pt-[4px] relative shrink-0" data-name="margin-wrap">
                    <Icon />
                  </div>
                  <Wrapper2 additionalClassNames="text-[#4b5563]">통계</Wrapper2>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div className="content-stretch flex flex-col h-[1024px] items-start p-[32px] relative shrink-0 w-[1184px]" data-name="DIV-50">
          <div className="content-stretch flex flex-col h-[870px] items-start relative shrink-0 w-[1120px]" data-name="DIV-51">
            <div className="content-stretch flex items-start pb-[32px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
              <div className="content-stretch flex flex-col h-[64px] items-start relative shrink-0 w-[1120px]" data-name="DIV-52">
                <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                  <div className="content-stretch flex h-[32px] items-center relative shrink-0 w-[1120px]" data-name="H2-53">
                    <div className="flex flex-col font-['Roboto:Bold','Noto_Sans_KR:Bold',sans-serif] font-bold justify-center leading-[0] relative shrink-0 text-[#111827] text-[24px] text-nowrap" style={{ fontVariationSettings: "'wdth' 100" }}>
                      <p className="leading-[32px]">새 과목 개설</p>
                    </div>
                  </div>
                </div>
                <div className="content-stretch flex h-[24px] items-center relative shrink-0 w-[1120px]" data-name="P-56">
                  <Wrapper2 additionalClassNames="text-[#4b5563]">단계별로 교육과목을 개설하고 설정할 수 있습니다.</Wrapper2>
                </div>
              </div>
            </div>
            <div className="content-stretch flex items-start pb-[24px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
              <div className="bg-white content-stretch flex flex-col h-[88px] items-start p-[24px] relative rounded-[8px] shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] shrink-0 w-[1120px]" data-name="DIV-59">
                <div className="content-stretch flex h-[40px] items-center justify-between relative shrink-0 w-[1072px]" data-name="DIV-60">
                  <div className="content-stretch flex h-[40px] items-center relative shrink-0 w-[203.016px]" data-name="DIV-61">
                    <div className="bg-[#2563eb] content-stretch flex items-center justify-center relative rounded-[9999px] shrink-0 size-[40px]" data-name="DIV-62">
                      <div className="content-stretch flex items-center justify-center relative shrink-0 size-[20px]" data-name="I-63">
                        <div className="absolute h-[16px] left-[1.67px] overflow-clip top-[2px] w-[16.656px]" data-name="Icon-64">
                          <div className="absolute flex inset-[8.33%_10%] items-center justify-center">
                            <div className="flex-none h-[13.333px] scale-y-[-100%] w-[13.325px]">
                              <Group>
                                <path d={svgPaths.p6420800} fill="var(--fill-0, white)" id="Vector" />
                              </Group>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex items-start pl-[12px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-stretch flex flex-col h-[20px] items-start relative shrink-0 w-[55.016px]" data-name="DIV-65">
                        <DivText text="기본 정보" additionalClassNames="w-[55.016px]" />
                      </div>
                    </div>
                    <div className="content-stretch flex items-start px-[16px] py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="bg-[#2563eb] h-[2px] shrink-0 w-[64px]" data-name="DIV-69" />
                    </div>
                  </div>
                  <div className="content-stretch flex h-[40px] items-center relative shrink-0 w-[215.891px]" data-name="DIV-70">
                    <div className="bg-[#2563eb] content-stretch flex items-center justify-center relative rounded-[9999px] shrink-0 size-[40px]" data-name="DIV-71">
                      <div className="content-stretch flex items-center justify-center relative shrink-0 size-[20px]" data-name="I-72">
                        <div className="absolute h-[16px] left-[1.67px] overflow-clip top-[2px] w-[16.656px]" data-name="Icon-73">
                          <div className="absolute flex inset-[6.25%_8%] items-center justify-center">
                            <div className="flex-none h-[14px] scale-y-[-100%] w-[13.991px]">
                              <Group>
                                <path d={svgPaths.pbed2c00} fill="var(--fill-0, white)" id="Vector" />
                              </Group>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex items-start pl-[12px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-stretch flex flex-col h-[20px] items-start relative shrink-0 w-[67.891px]" data-name="DIV-74">
                        <DivText text="학습자 선택" additionalClassNames="w-[67.891px]" />
                      </div>
                    </div>
                    <div className="content-stretch flex items-start px-[16px] py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="bg-[#2563eb] h-[2px] shrink-0 w-[64px]" data-name="DIV-78" />
                    </div>
                  </div>
                  <div className="content-stretch flex h-[40px] items-center relative shrink-0 w-[215.891px]" data-name="DIV-79">
                    <div className="bg-[#2563eb] content-stretch flex items-center justify-center relative rounded-[9999px] shrink-0 size-[40px]" data-name="DIV-80">
                      <div className="content-stretch flex items-center justify-center relative shrink-0 size-[20px]" data-name="I-81">
                        <div className="absolute h-[16px] left-[1.67px] overflow-clip top-[2px] w-[16.656px]" data-name="Icon-82">
                          <div className="absolute flex inset-[14.58%_6%] items-center justify-center">
                            <div className="flex-none h-[11.333px] scale-y-[-100%] w-[14.658px]">
                              <div className="relative size-full" data-name="Group">
                                <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 15 12">
                                  <g id="Group">
                                    <path d={svgPaths.p39c77f00} fill="var(--fill-0, white)" id="Vector" />
                                  </g>
                                </svg>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex items-start pl-[12px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-stretch flex flex-col h-[20px] items-start relative shrink-0 w-[67.891px]" data-name="DIV-83">
                        <DivText text="차시별 구성" additionalClassNames="w-[67.891px]" />
                      </div>
                    </div>
                    <div className="content-stretch flex items-start px-[16px] py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="bg-[#e5e7eb] h-[2px] shrink-0 w-[64px]" data-name="DIV-87" />
                    </div>
                  </div>
                  <div className="content-stretch flex h-[40px] items-center relative shrink-0 w-[107.016px]" data-name="DIV-88">
                    <div className="bg-[#e5e7eb] content-stretch flex items-center justify-center relative rounded-[9999px] shrink-0 size-[40px]" data-name="DIV-89">
                      <div className="content-stretch flex items-center justify-center relative shrink-0 size-[20px]" data-name="I-90">
                        <div className="absolute h-[16px] left-[1.67px] overflow-clip top-[2px] w-[16.656px]" data-name="Icon-91">
                          <div className="absolute flex inset-[24.92%_16.08%] items-center justify-center">
                            <div className="flex-none h-[8.027px] scale-y-[-100%] w-[11.3px]">
                              <div className="relative size-full" data-name="Group">
                                <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 12 9">
                                  <g id="Group">
                                    <path d={svgPaths.pd7e4600} fill="var(--fill-0, #4B5563)" id="Vector" />
                                  </g>
                                </svg>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex items-start pl-[12px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-stretch flex flex-col h-[20px] items-start relative shrink-0 w-[55.016px]" data-name="DIV-92">
                        <div className="content-stretch flex h-[20px] items-center relative shrink-0 w-[55.016px]" data-name="DIV-93">
                          <Wrapper additionalClassNames="text-[#6b7280]">최종 확인</Wrapper>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className="bg-white content-stretch flex flex-col h-[662px] items-start p-[32px] relative rounded-[8px] shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] shrink-0 w-[1120px]" data-name="DIV-96">
              <div className="content-stretch flex flex-col h-[501px] items-start relative shrink-0 w-[1056px]" data-name="DIV-97">
                <HText text="차시별 구성" additionalClassNames="w-[1056px]" />
                <div className="content-stretch flex h-[497px] items-start pb-0 pt-[24px] px-0 relative shrink-0" data-name="margin-wrap">
                  <div className="content-stretch flex flex-col h-[473px] items-start p-[25px] relative rounded-[8px] shrink-0 w-[1056px]" data-name="DIV-101">
                    <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                    <div className="content-stretch flex items-start pb-[16px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-stretch flex h-[24px] items-center relative shrink-0 w-[1006px]" data-name="DIV-102">
                        <div className="content-stretch flex h-[24px] items-center relative shrink-0 w-[38.547px]" data-name="H4-103">
                          <Wrapper3 additionalClassNames="text-[#111827]">1차시</Wrapper3>
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[283px] items-start relative shrink-0 w-[1006px]" data-name="DIV-106">
                      <div className="content-stretch flex flex-col h-[66px] items-start relative shrink-0 w-[1006px]" data-name="DIV-107">
                        <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                          <LabelText text="차시 제목" />
                        </div>
                        <div className="bg-white content-stretch flex h-[38px] items-center px-[13px] py-[9px] relative rounded-[6px] shrink-0 w-[1006px]" data-name="INPUT">
                          <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[6px]" />
                          <div className="absolute flex flex-col font-['Inter:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium justify-center leading-[0] left-[13px] not-italic text-[#9ca3af] text-[14px] top-[19px] translate-y-[-50%] w-[980px]">
                            <p className="leading-[20px]">예: Python 기초 문법</p>
                          </div>
                        </div>
                      </div>
                      <div className="content-stretch flex items-start pb-0 pt-[16px] px-0 relative shrink-0" data-name="margin-wrap">
                        <div className="content-stretch flex flex-col h-[113px] items-start relative shrink-0 w-[1006px]" data-name="DIV-112">
                          <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                            <LabelText text="차시 설명" />
                          </div>
                          <div className="bg-white h-[78px] relative rounded-[6px] shrink-0 w-[1006px]" data-name="TEXTAREA">
                            <div className="content-stretch flex items-start overflow-clip px-[13px] py-[9px] relative rounded-[inherit] size-full">
                              <p className="-webkit-box absolute font-['Inter:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium leading-[20px] left-[13px] not-italic overflow-ellipsis overflow-hidden text-[#9ca3af] text-[14px] top-[9px] w-[980px]">이 차시에서 학습할 내용을 설명하세요</p>
                            </div>
                            <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[6px]" />
                          </div>
                        </div>
                      </div>
                      <div className="content-stretch flex items-start pb-0 pt-[16px] px-0 relative shrink-0" data-name="margin-wrap">
                        <div className="content-stretch flex flex-col h-[72px] items-start relative shrink-0 w-[1006px]" data-name="DIV-117">
                          <LabelText text="강의 영상" />
                          <div className="content-stretch flex items-start pb-0 pt-[16px] px-0 relative shrink-0" data-name="margin-wrap">
                            <div className="content-stretch flex h-[36px] items-start relative shrink-0 w-[1006px]" data-name="DIV-121">
                              <div className="bg-[#16a34a] content-stretch flex h-[36px] items-center justify-center px-[16px] py-[8px] relative rounded-[6px] shrink-0 w-[99.875px]" data-name="BUTTON-122">
                                <div className="flex flex-col font-['Roboto:Regular','Noto_Sans_KR:Regular',sans-serif] font-normal justify-center leading-[0] relative shrink-0 text-[14px] text-center text-nowrap text-white" style={{ fontVariationSettings: "'wdth' 100" }}>
                                  <p className="leading-[20px]">콘텐츠 검색</p>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div className="content-stretch flex flex-col h-[87px] items-start pb-0 pt-[21px] px-0 relative shrink-0 w-[1006px]" data-name="DIV-107">
                        <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                          <LabelText text="키 또는 URL" />
                        </div>
                        <div className="bg-white content-stretch flex h-[38px] items-center px-[13px] py-[9px] relative rounded-[6px] shrink-0 w-[1006px]" data-name="INPUT">
                          <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[6px]" />
                          <div className="absolute flex flex-col font-['Inter:Medium',sans-serif] font-medium justify-center leading-[0] left-[13px] not-italic text-[#9ca3af] text-[14px] top-[19px] translate-y-[-50%] w-[980px]">
                            <p className="leading-[20px]">youtube.com</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div className="content-stretch flex items-start pb-0 pt-[24px] px-0 relative shrink-0" data-name="margin-wrap">
                  <div className="content-stretch flex h-[52px] items-center justify-center px-[2px] py-[14px] relative rounded-[8px] shrink-0 w-[1056px]" data-name="BUTTON-125">
                    <div aria-hidden="true" className="absolute border-2 border-[#d1d5db] border-dashed inset-0 pointer-events-none rounded-[8px]" />
                    <Wrapper2 additionalClassNames="text-[#4b5563] text-center">+ 차시 추가</Wrapper2>
                  </div>
                </div>
                <div className="content-stretch flex items-start pb-0 pt-[32px] px-0 relative shrink-0" data-name="margin-wrap">
                  <div className="content-stretch flex h-[65px] items-start justify-between pb-0 pt-[25px] px-0 relative shrink-0 w-[1056px]" data-name="DIV-128">
                    <div aria-hidden="true" className="absolute border-[#e5e7eb] border-[1px_0px_0px] border-solid inset-0 pointer-events-none" />
                    <div className="bg-[#e5e7eb] content-stretch flex h-[40px] items-center justify-center px-[24px] py-[8px] relative rounded-[6px] shrink-0 w-[77.453px]" data-name="BUTTON-129">
                      <Wrapper2 additionalClassNames="text-[#374151] text-center">이전</Wrapper2>
                    </div>
                    <div className="bg-[#2563eb] content-stretch flex h-[40px] items-center justify-center px-[24px] py-[8px] relative rounded-[6px] shrink-0 w-[77.453px]" data-name="BUTTON-132">
                      <Wrapper2 additionalClassNames="text-center text-white">다음</Wrapper2>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="absolute content-stretch flex flex-col items-start left-1/2 top-0 translate-x-[-50%] w-[1440px]" data-name="교수 상단바">
        <div className="bg-white h-[72px] relative shadow-[0px_4px_4px_0px_rgba(0,0,0,0.05)] shrink-0 w-full" data-name="프레임">
          <div className="overflow-clip rounded-[inherit] size-full">
            <div className="content-stretch flex items-start px-[32px] py-[16px] relative size-full">
              <div className="content-stretch flex h-[40px] items-start relative shrink-0 w-[1377px]" data-name="DIV-5">
                <div className="h-[40px] relative shrink-0 w-[43px]" data-name="폴리텍 로고 2">
                  <img alt="" className="absolute inset-0 max-w-none object-50%-50% object-cover pointer-events-none size-full" src={img2} />
                </div>
                <div className="content-stretch flex h-[40px] items-center px-[10px] py-0 relative shrink-0 w-[134px]" data-name="DIV-8">
                  <div className="flex flex-col justify-center leading-[16px] relative shrink-0 text-[12px] text-nowrap font-semibold text-blue-700">
                    <p className="mb-0">한국폴리텍대학</p>
                    <p>미래형 직업교육 플랫폼</p>
                  </div>
                </div>
                <div className="content-stretch flex h-[40px] items-center relative shrink-0 w-[127px]" data-name="DIV-6">
                  <div className="content-stretch flex h-[40px] items-start pl-[16px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                    <div className="content-stretch flex h-[40px] items-start relative shrink-0 w-[70px]" data-name="DIV-10">
                      <div className="flex flex-col font-['Roboto:Regular',sans-serif] font-normal h-[40px] justify-center leading-[0] relative shrink-0 text-[#6b7280] text-[16px] w-[4px]" style={{ fontVariationSettings: "'wdth' 100" }}>
                        <p className="leading-[24px]">|</p>
                      </div>
                      <div className="h-[0.01px] opacity-0 shrink-0 w-[16px]" data-name="split-margin-15" />
                      <Wrapper1 additionalClassNames="h-[40px] text-[#1f2937] w-[50px]">교수자</Wrapper1>
                    </div>
                  </div>
                </div>
                <div className="content-stretch flex h-[40px] items-center justify-end relative shrink-0 w-[1072px]" data-name="DIV-18">
                  <div className="bg-[#16a34a] content-stretch flex h-[40px] items-center px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[148.281px]" data-name="A-19">
                    <div className="content-stretch flex items-start pl-0 pr-[8px] py-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-stretch flex h-[24px] items-center relative shrink-0 w-[16px]" data-name="I-20">
                        <Icon1 additionalClassNames="size-[16px] top-[4px]">
                          <div className="flex-none h-[14px] scale-y-[-100%] w-[10.24px]">
                            <div className="relative size-full" data-name="Group">
                              <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 11 14">
                                <g id="Group">
                                  <path d={svgPaths.p2005ea80} fill="var(--fill-0, white)" id="Vector" />
                                </g>
                              </svg>
                            </div>
                          </div>
                        </Icon1>
                      </div>
                    </div>
                    <Wrapper2 additionalClassNames="text-white">학습자 페이지</Wrapper2>
                  </div>
                  <div className="content-stretch flex items-start pl-[16px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                    <div className="content-stretch flex h-[32px] items-center relative shrink-0 w-[88.172px]" data-name="DIV-24">
                      <div className="bg-[#2563eb] content-stretch flex items-center justify-center relative rounded-[9999px] shrink-0 size-[32px]" data-name="DIV-25">
                        <div className="content-stretch flex h-[20px] items-center relative shrink-0 w-[14.016px]" data-name="I-26">
                          <Icon1 additionalClassNames="h-[14px] top-[3px] w-[14.016px]">
                            <div className="flex-none h-[12.25px] scale-y-[-100%] w-[8.97px]">
                              <div className="relative size-full" data-name="Group">
                                <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 9 13">
                                  <g id="Group">
                                    <path d={svgPaths.p29c18680} fill="var(--fill-0, white)" id="Vector" />
                                  </g>
                                </svg>
                              </div>
                            </div>
                          </Icon1>
                        </div>
                      </div>
                      <div className="content-stretch flex items-start pl-[12px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                        <div className="content-stretch flex h-[24px] items-center relative shrink-0 w-[44.172px]" data-name="SPAN-28">
                          <Wrapper3 additionalClassNames="text-[#374151]">김교수</Wrapper3>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
