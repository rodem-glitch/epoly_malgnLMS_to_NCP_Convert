import svgPaths from "./svg-1qyxtgu647";
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
type Wrapper5Props = {
  additionalClassNames?: string;
};

function Wrapper5({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper5Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:SemiBold','Noto_Sans_KR:Bold',sans-serif] font-semibold justify-center leading-[0] relative shrink-0 text-[18px]", additionalClassNames)}>
      <p className="leading-[28px]">{children}</p>
    </div>
  );
}
type Wrapper4Props = {
  additionalClassNames?: string;
};

function Wrapper4({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper4Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium justify-center leading-[0] relative shrink-0 text-[16px] text-nowrap", additionalClassNames)}>
      <p className="leading-[24px]">{children}</p>
    </div>
  );
}
type Wrapper3Props = {
  additionalClassNames?: string;
};

function Wrapper3({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper3Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Regular','Noto_Sans_KR:Regular',sans-serif] font-normal justify-center leading-[0] relative shrink-0 text-[16px] text-nowrap", additionalClassNames)}>
      <p className="leading-[24px]">{children}</p>
    </div>
  );
}
type Wrapper2Props = {
  additionalClassNames?: string;
};

function Wrapper2({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper2Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium justify-center leading-[0] relative shrink-0 text-[14px] text-nowrap", additionalClassNames)}>
      <p className="leading-[20px]">{children}</p>
    </div>
  );
}
type Wrapper1Props = {
  additionalClassNames?: string;
};

function Wrapper1({ children, additionalClassNames = "" }: React.PropsWithChildren<Wrapper1Props>) {
  return (
    <div style={{ fontVariationSettings: "'wdth' 100" }} className={clsx("flex flex-col font-['Roboto:Regular','Noto_Sans_KR:Regular',sans-serif] font-normal justify-center leading-[0] relative shrink-0 text-[14px] text-nowrap", additionalClassNames)}>
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
type WrapperProps = {
  additionalClassNames?: string;
};

function Wrapper({ children, additionalClassNames = "" }: React.PropsWithChildren<WrapperProps>) {
  return (
    <div className={clsx("content-stretch flex h-[16px] items-center relative shrink-0", additionalClassNames)}>
      <div className="flex flex-col font-['Roboto:Regular','Noto_Sans_KR:Regular',sans-serif] font-normal justify-center leading-[0] relative shrink-0 text-[#9ca3af] text-[12px] text-nowrap" style={{ fontVariationSettings: "'wdth' 100" }}>
        <p className="leading-[16px]">{children}</p>
      </div>
    </div>
  );
}

function Div3() {
  return <Wrapper additionalClassNames="w-[279.328px]">{`대구캠퍼스 `}</Wrapper>;
}
type Div2Props = {
  additionalClassNames?: string;
};

function Div2({ additionalClassNames = "" }: Div2Props) {
  return <Wrapper>{`부산캠퍼스 `}</Wrapper>;
}
type Div1Props = {
  additionalClassNames?: string;
};

function Div1({ additionalClassNames = "" }: Div1Props) {
  return <Wrapper>{`서울캠퍼스 `}</Wrapper>;
}
type DivText3Props = {
  text: string;
  additionalClassNames?: string;
};

function DivText3({ text, additionalClassNames = "" }: DivText3Props) {
  return (
    <div className={clsx("content-stretch flex h-[20px] items-center relative shrink-0", additionalClassNames)}>
      <Wrapper1 additionalClassNames="text-[#6b7280]">{text}</Wrapper1>
    </div>
  );
}
type DivText2Props = {
  text: string;
  additionalClassNames?: string;
};

function DivText2({ text, additionalClassNames = "" }: DivText2Props) {
  return (
    <div className={clsx("content-stretch flex h-[24px] items-center relative shrink-0", additionalClassNames)}>
      <Wrapper4 additionalClassNames="text-[#111827]">{text}</Wrapper4>
    </div>
  );
}

function Div() {
  return (
    <div className="relative rounded-[4px] shrink-0 size-[16px]">
      <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[4px]" />
    </div>
  );
}
type SelectTextProps = {
  text: string;
};

function SelectText({ text }: SelectTextProps) {
  return (
    <div className="bg-[#efefef] content-stretch flex flex-col h-[36px] items-start pl-[13px] pr-[33px] py-[9px] relative rounded-[6px] shrink-0 w-[244px]">
      <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[6px]" />
      <Wrapper1 additionalClassNames="text-[#111827]">{text}</Wrapper1>
      <div className="absolute left-[229px] size-[10px] top-[13px]">
        <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 10 10">
          <g id="select-icon-113">
            <path d="M1 3.5L5 7.5L9 3.5H1Z" fill="var(--fill-0, black)" id="Vector" />
          </g>
        </svg>
      </div>
    </div>
  );
}
type LabelTextProps = {
  text: string;
};

function LabelText({ text }: LabelTextProps) {
  return (
    <div className="content-stretch flex h-[20px] items-center relative shrink-0 w-[244px]">
      <Wrapper2 additionalClassNames="text-[#374151]">{text}</Wrapper2>
    </div>
  );
}
type DivText1Props = {
  text: string;
  additionalClassNames?: string;
};

function DivText1({ text, additionalClassNames = "" }: DivText1Props) {
  return (
    <div className={clsx("content-stretch flex h-[20px] items-center relative shrink-0", additionalClassNames)}>
      <Wrapper2 additionalClassNames="text-[#6b7280]">{text}</Wrapper2>
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
      <Wrapper2 additionalClassNames="text-[#2563eb]">{text}</Wrapper2>
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
      <Wrapper5 additionalClassNames="text-[#111827] text-nowrap">{text}</Wrapper5>
    </div>
  );
}

export default function Component() {
  return (
    <div className="bg-white relative size-full" data-name="교수 과목개설 학습자선택(선택됨)">
      <div className="absolute content-stretch flex h-[1085px] items-start left-0 top-[72px] w-[1440px]" data-name="DIV-31">
        <div className="bg-white content-stretch flex flex-col h-[1085px] items-start relative shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] shrink-0 w-[256px]" data-name="DIV-32">
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
                <Wrapper3 additionalClassNames="text-[#4b5563]">과목 관리</Wrapper3>
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
                  <Wrapper3 additionalClassNames="text-[#1d4ed8]">과목 개설</Wrapper3>
                </div>
              </div>
              <div className="content-stretch flex items-start pb-0 pt-[8px] px-0 relative shrink-0" data-name="margin-wrap">
                <div className="content-stretch flex h-[41px] items-start px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[208px]" data-name="BUTTON-46">
                  <div className="content-stretch flex items-start pb-0 pl-0 pr-[12px] pt-[4px] relative shrink-0" data-name="margin-wrap">
                    <Icon />
                  </div>
                  <Wrapper3 additionalClassNames="text-[#4b5563]">콘텐츠 라이브러리</Wrapper3>
                </div>
              </div>
              <div className="content-stretch flex items-start pb-0 pt-[8px] px-0 relative shrink-0" data-name="margin-wrap">
                <div className="content-stretch flex h-[41px] items-start px-[16px] py-[8px] relative rounded-[8px] shrink-0 w-[208px]" data-name="BUTTON-46">
                  <div className="content-stretch flex items-start pb-0 pl-0 pr-[12px] pt-[4px] relative shrink-0" data-name="margin-wrap">
                    <Icon />
                  </div>
                  <Wrapper3 additionalClassNames="text-[#4b5563]">통계</Wrapper3>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div className="content-stretch flex flex-col h-[1085px] items-start p-[32px] relative shrink-0 w-[1184px]" data-name="DIV-50">
          <div className="content-stretch flex flex-col h-[1021px] items-start relative shrink-0 w-[1120px]" data-name="DIV-51">
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
                  <Wrapper3 additionalClassNames="text-[#4b5563]">단계별로 교육과목을 개설하고 설정할 수 있습니다.</Wrapper3>
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
                      <div className="bg-[#e5e7eb] h-[2px] shrink-0 w-[64px]" data-name="DIV-78" />
                    </div>
                  </div>
                  <div className="content-stretch flex h-[40px] items-center relative shrink-0 w-[215.891px]" data-name="DIV-79">
                    <div className="bg-[#e5e7eb] content-stretch flex items-center justify-center relative rounded-[9999px] shrink-0 size-[40px]" data-name="DIV-80">
                      <div className="content-stretch flex items-center justify-center relative shrink-0 size-[20px]" data-name="I-81">
                        <div className="absolute h-[16px] left-[1.67px] overflow-clip top-[2px] w-[16.656px]" data-name="Icon-82">
                          <div className="absolute flex inset-[14.58%_6%] items-center justify-center">
                            <div className="flex-none h-[11.333px] scale-y-[-100%] w-[14.658px]">
                              <div className="relative size-full" data-name="Group">
                                <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 15 12">
                                  <g id="Group">
                                    <path d={svgPaths.p39c77f00} fill="var(--fill-0, #4B5563)" id="Vector" />
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
                        <DivText1 text="차시별 구성" additionalClassNames="w-[67.891px]" />
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
                        <DivText1 text="최종 확인" additionalClassNames="w-[55.016px]" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className="bg-white content-stretch flex flex-col h-[813px] items-start p-[32px] relative rounded-[8px] shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] shrink-0 w-[1120px]" data-name="DIV-96">
              <div className="content-stretch flex flex-col h-[652px] items-start relative shrink-0 w-[1056px]" data-name="DIV-97">
                <HText text="학습자 선택" additionalClassNames="w-[1056px]" />
                <div className="content-stretch flex items-start pb-0 pt-[24px] px-0 relative shrink-0" data-name="margin-wrap">
                  <div className="bg-[#f9fafb] content-stretch flex flex-col h-[152px] items-start p-[16px] relative rounded-[8px] shrink-0 w-[1056px]" data-name="DIV-101">
                    <div className="content-stretch flex items-start pb-[16px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                      <div className="content-start flex flex-wrap gap-[16px] h-[66px] items-start relative shrink-0 w-[1024px]" data-name="DIV-102">
                        <div className="content-stretch flex flex-col h-[66px] items-start relative shrink-0 w-[244px]" data-name="DIV-103">
                          <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                            <LabelText text="이름 검색" />
                          </div>
                          <div className="content-stretch flex flex-col h-[38px] items-start relative shrink-0 w-[244px]" data-name="DIV-107">
                            <div className="bg-white content-stretch flex h-[38px] items-center pl-[41px] pr-[17px] py-[9px] relative rounded-[6px] shrink-0 w-[244px]" data-name="INPUT">
                              <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[6px]" />
                              <div className="absolute flex flex-col font-['Inter:Medium','Noto_Sans_KR:Medium',sans-serif] font-medium justify-center leading-[0] left-[41px] not-italic text-[#9ca3af] text-[14px] top-[19px] translate-y-[-50%] w-[186px]">
                                <p className="leading-[20px]">학습자 이름 검색...</p>
                              </div>
                              <div className="flex flex-col font-['Roboto:Regular',sans-serif] font-normal justify-center leading-[0] overflow-ellipsis overflow-hidden relative shrink-0 text-[#111827] text-[14px] w-[186px]" style={{ fontVariationSettings: "'wdth' 100" }}>
                                <p className="leading-[20px]">&nbsp;</p>
                              </div>
                            </div>
                            <div className="absolute left-[12px] overflow-clip size-[16px] top-[11px]" data-name="Icon-109">
                              <div className="absolute flex inset-[7.67%_9.36%] items-center justify-center">
                                <div className="flex-none h-[13.547px] scale-y-[-100%] w-[13.005px]">
                                  <div className="relative size-full" data-name="Group">
                                    <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 13 14">
                                      <g id="Group">
                                        <path d={svgPaths.p3c74000} fill="var(--fill-0, #9CA3AF)" id="Vector" />
                                      </g>
                                    </svg>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                        <div className="content-stretch flex flex-col h-[66px] items-start relative shrink-0 w-[244px]" data-name="DIV-110">
                          <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                            <LabelText text="캠퍼스" />
                          </div>
                          <SelectText text="전체" />
                        </div>
                        <div className="content-stretch flex flex-col h-[66px] items-start relative shrink-0 w-[244px]" data-name="DIV-117">
                          <div className="content-stretch flex items-start pb-[8px] pt-0 px-0 relative shrink-0" data-name="margin-wrap">
                            <LabelText text="전공" />
                          </div>
                          <SelectText text="전체" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex h-[38px] items-center justify-between relative shrink-0 w-[1024px]" data-name="DIV-131">
                      <div className="content-stretch flex h-[38px] items-start relative shrink-0 w-[246.438px]" data-name="DIV-132">
                        <div className="bg-[#2563eb] content-stretch flex h-[38px] items-start justify-center px-[16px] py-[8px] relative rounded-[6px] shrink-0 w-[87px]" data-name="BUTTON-133">
                          <Wrapper1 additionalClassNames="text-center text-white">전체 선택</Wrapper1>
                        </div>
                        <div className="content-stretch flex items-start pl-[12px] pr-0 py-0 relative shrink-0" data-name="margin-wrap">
                          <div className="content-stretch flex h-[38px] items-center justify-center px-[17px] py-[9px] relative rounded-[6px] shrink-0 w-[147.438px]" data-name="BUTTON-136">
                            <div aria-hidden="true" className="absolute border border-[#d1d5db] border-solid inset-0 pointer-events-none rounded-[6px]" />
                            <Wrapper1 additionalClassNames="text-[#374151] text-center">현재 목록 전체 해제</Wrapper1>
                          </div>
                        </div>
                      </div>
                      <div className="content-stretch flex h-[20px] items-center relative shrink-0 w-[167.031px]" data-name="DIV-139">
                        <Wrapper1 additionalClassNames="text-[#4b5563]">검색 결과: 12명 | 선택됨: 1명</Wrapper1>
                      </div>
                    </div>
                  </div>
                </div>
                <div className="content-stretch flex items-start pb-0 pt-[24px] px-0 relative shrink-0" data-name="margin-wrap">
                  <div className="content-start flex flex-wrap gap-[16px] h-[424px] items-start relative shrink-0 w-[1056px]" data-name="DIV-142">
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-143">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-144">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-146">
                          <DivText2 text="김학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="컴퓨터공학과" additionalClassNames="w-[279.328px]" />
                          <Div1 additionalClassNames="w-[279.328px]" />
                        </div>
                      </div>
                    </div>
                    <div className="bg-[#eff6ff] content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-156">
                      <div aria-hidden="true" className="absolute border border-[#3b82f6] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-157">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <div className="bg-[#2563eb] content-stretch flex items-center justify-center p-px relative rounded-[4px] shrink-0 size-[16px]" data-name="DIV-158">
                            <div aria-hidden="true" className="absolute border border-[#2563eb] border-solid inset-0 pointer-events-none rounded-[4px]" />
                            <div className="overflow-clip relative shrink-0 size-[12px]" data-name="Icon-159">
                              <div className="absolute flex inset-[24.92%_16.08%] items-center justify-center">
                                <div className="flex-none h-[6.02px] scale-y-[-100%] w-[8.141px]">
                                  <div className="relative size-full" data-name="Group">
                                    <svg className="block size-full" fill="none" preserveAspectRatio="none" viewBox="0 0 9 7">
                                      <g id="Group">
                                        <path d={svgPaths.p364bf8f0} fill="var(--fill-0, white)" id="Vector" />
                                      </g>
                                    </svg>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-160">
                          <DivText2 text="이학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="전자공학과" additionalClassNames="w-[279.328px]" />
                          <Div2 additionalClassNames="w-[279.328px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.344px]" data-name="DIV-170">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.344px]" data-name="DIV-171">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.344px]" data-name="DIV-173">
                          <DivText2 text="박학생" additionalClassNames="w-[279.344px]" />
                          <DivText3 text="기계공학과" additionalClassNames="w-[279.344px]" />
                          <Div1 additionalClassNames="w-[279.344px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-183">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-184">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-186">
                          <DivText2 text="최학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="컴퓨터공학과" additionalClassNames="w-[279.328px]" />
                          <Div3 />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-196">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-197">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-199">
                          <DivText2 text="정학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="전자공학과" additionalClassNames="w-[279.328px]" />
                          <Div1 additionalClassNames="w-[279.328px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.344px]" data-name="DIV-209">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.344px]" data-name="DIV-210">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.344px]" data-name="DIV-212">
                          <DivText2 text="강학생" additionalClassNames="w-[279.344px]" />
                          <DivText3 text="기계공학과" additionalClassNames="w-[279.344px]" />
                          <Div2 additionalClassNames="w-[279.344px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-222">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-223">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-225">
                          <DivText2 text="윤학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="컴퓨터공학과" additionalClassNames="w-[279.328px]" />
                          <Div1 additionalClassNames="w-[279.328px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-235">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-236">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-238">
                          <DivText2 text="조학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="전자공학과" additionalClassNames="w-[279.328px]" />
                          <Div3 />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.344px]" data-name="DIV-248">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.344px]" data-name="DIV-249">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.344px]" data-name="DIV-251">
                          <DivText2 text="한학생" additionalClassNames="w-[279.344px]" />
                          <DivText3 text="기계공학과" additionalClassNames="w-[279.344px]" />
                          <Div2 additionalClassNames="w-[279.344px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-261">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-262">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-264">
                          <DivText2 text="신학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="컴퓨터공학과" additionalClassNames="w-[279.328px]" />
                          <Div1 additionalClassNames="w-[279.328px]" />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.328px]" data-name="DIV-274">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.328px]" data-name="DIV-275">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.328px]" data-name="DIV-277">
                          <DivText2 text="오학생" additionalClassNames="w-[279.328px]" />
                          <DivText3 text="전자공학과" additionalClassNames="w-[279.328px]" />
                          <Div3 />
                        </div>
                      </div>
                    </div>
                    <div className="content-stretch flex flex-col h-[94px] items-start p-[17px] relative rounded-[8px] shrink-0 w-[341.344px]" data-name="DIV-287">
                      <div aria-hidden="true" className="absolute border border-[#e5e7eb] border-solid inset-0 pointer-events-none rounded-[8px]" />
                      <div className="content-stretch flex h-[60px] items-center relative shrink-0 w-[307.344px]" data-name="DIV-288">
                        <div className="content-stretch flex items-start pl-0 pr-[12px] py-0 relative shrink-0" data-name="margin-wrap">
                          <Div />
                        </div>
                        <div className="content-stretch flex flex-col h-[60px] items-start relative shrink-0 w-[279.344px]" data-name="DIV-290">
                          <DivText2 text="임학생" additionalClassNames="w-[279.344px]" />
                          <DivText3 text="기계공학과" additionalClassNames="w-[279.344px]" />
                          <Div2 additionalClassNames="w-[279.344px]" />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div className="content-stretch flex items-start pb-0 pt-[32px] px-0 relative shrink-0" data-name="margin-wrap">
                <div className="content-stretch flex h-[65px] items-start justify-between pb-0 pt-[25px] px-0 relative shrink-0 w-[1056px]" data-name="DIV-300">
                  <div aria-hidden="true" className="absolute border-[#e5e7eb] border-[1px_0px_0px] border-solid inset-0 pointer-events-none" />
                  <div className="bg-[#e5e7eb] content-stretch flex h-[40px] items-center justify-center px-[24px] py-[8px] relative rounded-[6px] shrink-0 w-[77.453px]" data-name="BUTTON-301">
                    <Wrapper3 additionalClassNames="text-[#374151] text-center">이전</Wrapper3>
                  </div>
                  <div className="bg-[#2563eb] content-stretch flex h-[40px] items-center justify-center px-[24px] py-[8px] relative rounded-[6px] shrink-0 w-[77.453px]" data-name="BUTTON-304">
                    <Wrapper3 additionalClassNames="text-center text-white">다음</Wrapper3>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="absolute content-stretch flex flex-col items-start left-0 top-0 w-[1440px]" data-name="교수 상단바">
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
                      <Wrapper5 additionalClassNames="h-[40px] text-[#1f2937] w-[50px]">교수자</Wrapper5>
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
                    <Wrapper3 additionalClassNames="text-white">학습자 페이지</Wrapper3>
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
                          <Wrapper4 additionalClassNames="text-[#374151]">김교수</Wrapper4>
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
